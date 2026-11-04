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
        DataSource cached = userDataSourceCache.get(username);
        if (cached != null) {
            return cached;
        }
        DataSource created = createUserDataSource(username);
        if (created == null) {
            // 创建失败不缓存：本次请求使用默认数据源，下次请求仍会重试建池，
            // 避免数据库临时抖动后用户被永久钉死在默认数据源上
            return defaultDataSource;
        }
        DataSource existing = userDataSourceCache.putIfAbsent(username, created);
        if (existing != null) {
            if (created instanceof HikariDataSource hikari && !hikari.isClosed()) {
                hikari.close();
            }
            return existing;
        }
        return created;
    }

    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    private DataSource createUserDataSource(String username) {
        String url = "";
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
            if (entries.isEmpty()) {
                log.info("用户 {} 无自定义数据源配置，使用默认数据源", username);
                return defaultDataSource;
            }
            url = String.valueOf(entries.getOrDefault("url", ""));
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
            if (url.contains("mysql") && !url.contains("allowPublicKeyRetrieval")) {
                // MySQL 8 默认 caching_sha2_password，缺少该参数会报 Public Key Retrieval is not allowed
                config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            }
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
            log.error("为用户 {} 创建数据源失败(pool=UserPool-{}), url=[{}], 本次回退默认数据源, 下次请求将重试. 原因: {}",
                    username, username, url, e.getMessage());
            log.warn("请检查用户 {} 的JDBC URL：后端运行在容器内，localhost/127.0.0.1 指向容器自身而非数据库宿主机，" +
                    "应使用docker服务名(如 mysql)或宿主机IP；同时确认端口、库名及账号密码", username);
            return null;
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
