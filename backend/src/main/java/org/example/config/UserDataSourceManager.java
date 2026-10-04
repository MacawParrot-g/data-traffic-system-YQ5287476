package org.example.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(UserDataSourceManager.class);
    private static final String DS_CONFIG_KEY = "ds:config:";
    public UserDataSourceManager(@Qualifier("defaultDataSource") DataSource defaultDataSource,
                                 @Qualifier("dataSourceRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.defaultDataSource = defaultDataSource;
        this.redisTemplate = redisTemplate;
    }

    private final DataSource defaultDataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, DataSource> userDataSourceCache = new ConcurrentHashMap<>();

    public DataSource getDataSource(String username) {
        if (username == null || username.isEmpty()) {
            return defaultDataSource;
        }
        return userDataSourceCache.computeIfAbsent(username, this::createUserDataSource);
    }

    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    private DataSource createUserDataSource(String username) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
            if (entries.isEmpty()) {
                log.info("用户 {} 无自定义数据源配置，使用默认数据源", username);
                return defaultDataSource;
            }
            String url = String.valueOf(entries.getOrDefault("url", ""));
            String dsUsername = String.valueOf(entries.getOrDefault("username", ""));
            Object pwdObj = entries.get("password");
            String password = pwdObj != null ? pwdObj.toString() : "";

            if (url.isEmpty() || dsUsername.isEmpty()) {
                log.warn("用户 {} 数据源配置不完整，使用默认数据源", username);
                return defaultDataSource;
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(dsUsername);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setPoolName("UserPool-" + username);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(15000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setConnectionTestQuery("SELECT 1");

            HikariDataSource ds = new HikariDataSource(config);
            log.info("为用户 {} 创建自定义数据源连接池: {}", username, url);
            return ds;
        } catch (Exception e) {
            log.error("为用户 {} 创建数据源失败，回退默认: {}", username, e.getMessage());
            return defaultDataSource;
        }
    }

    public void evictUserDataSource(String username) {
        DataSource removed = userDataSourceCache.remove(username);
        if (removed instanceof HikariDataSource hikari && removed != defaultDataSource && !hikari.isClosed()) {
            hikari.close();
            log.info("已关闭用户 {} 的自定义数据源连接池", username);
        }
    }

    public boolean hasCustomConfig(String username) {
        if (username == null || username.isEmpty()) return false;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
        return !entries.isEmpty();
    }
}
