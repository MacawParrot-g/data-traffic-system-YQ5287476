package org.example.service.impl;

import org.example.common.Result;
import org.example.config.UserDataSourceManager;
import org.example.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceServiceImpl.class);
    private static final String DS_CONFIG_KEY = "ds:config:";
    private static final String DS_SWITCH_KEY = "ds:switch:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserDataSourceManager userDataSourceManager;

    public DataSourceServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                 UserDataSourceManager userDataSourceManager) {
        this.redisTemplate = redisTemplate;
        this.userDataSourceManager = userDataSourceManager;
    }

    @Override
    public Result getConfig(String username) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
            if (entries.isEmpty()) {
                return Result.success("未配置自定义数据源", null);
            }
            Map<String, Object> config = new HashMap<>();
            config.put("url", entries.getOrDefault("url", "").toString());
            config.put("username", entries.getOrDefault("username", "").toString());
            config.put("hasPassword", entries.containsKey("password") && entries.get("password") != null);
            return Result.success("查询成功", config);
        } catch (Exception e) {
            return Result.fail("查询数据源配置失败：" + e.getMessage());
        }
    }

    @Override
    public Result saveConfig(String username, String url, String dsUsername, String password) {
        if (url == null || url.isBlank()) {
            return Result.fail("数据源URL不能为空");
        }
        if (dsUsername == null || dsUsername.isBlank()) {
            return Result.fail("数据源用户名不能为空");
        }
        try {
            userDataSourceManager.evictUserDataSource(username);

            Map<String, Object> config = new HashMap<>();
            config.put("url", url.trim());
            config.put("username", dsUsername.trim());
            if (password != null && !password.isEmpty()) {
                config.put("password", password);
            }
            redisTemplate.opsForHash().putAll(DS_CONFIG_KEY + username, config);
            log.info("用户 {} 保存了自定义数据源配置: {}", username, url);
            return Result.success("数据源配置保存成功", null);
        } catch (Exception e) {
            return Result.fail("保存数据源配置失败：" + e.getMessage());
        }
    }

    @Override
    public Result deleteConfig(String username) {
        try {
            userDataSourceManager.evictUserDataSource(username);
            redisTemplate.delete(DS_CONFIG_KEY + username);
            redisTemplate.delete(DS_SWITCH_KEY + username);
            log.info("用户 {} 删除了自定义数据源配置", username);
            return Result.success("数据源配置已删除，已恢复默认数据源", null);
        } catch (Exception e) {
            return Result.fail("删除数据源配置失败：" + e.getMessage());
        }
    }

    @Override
    public Result testConnection(String url, String dsUsername, String password) {
        if (url == null || url.isBlank()) {
            return Result.fail("数据源URL不能为空");
        }
        if (dsUsername == null || dsUsername.isBlank()) {
            return Result.fail("数据源用户名不能为空");
        }
        DriverManagerDataSource ds = null;
        try {
            ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(url);
            ds.setUsername(dsUsername);
            ds.setPassword(password != null ? password : "");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                String dbVersion = jdbc.queryForObject("SELECT VERSION()", String.class);
                Map<String, Object> info = new HashMap<>();
                info.put("version", dbVersion);
                return Result.success("连接成功", info);
            }
            return Result.fail("连接测试失败");
        } catch (Exception e) {
            return Result.fail("连接失败：" + e.getMessage());
        }
    }

    @Override
    public Result getStatus(String username) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
            boolean hasConfig = !entries.isEmpty();
            Object switchVal = redisTemplate.opsForValue().get(DS_SWITCH_KEY + username);
            boolean useCustom = Boolean.TRUE.equals(switchVal) && hasConfig;

            Map<String, Object> status = new HashMap<>();
            status.put("hasConfig", hasConfig);
            status.put("useCustom", useCustom);
            status.put("currentSource", useCustom ? "自定义数据源" : "默认数据源");
            return Result.success("查询成功", status);
        } catch (Exception e) {
            return Result.fail("查询数据源状态失败：" + e.getMessage());
        }
    }

    @Override
    public Result switchDataSource(String username, Boolean useCustom) {
        try {
            if (Boolean.TRUE.equals(useCustom)) {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(DS_CONFIG_KEY + username);
                if (entries.isEmpty()) {
                    return Result.fail("请先配置自定义数据源后再切换");
                }
                redisTemplate.opsForValue().set(DS_SWITCH_KEY + username, true);
                log.info("用户 {} 切换到自定义数据源", username);
                return Result.success("已切换到自定义数据源", null);
            } else {
                redisTemplate.delete(DS_SWITCH_KEY + username);
                log.info("用户 {} 切换到默认数据源", username);
                return Result.success("已切换到默认数据源", null);
            }
        } catch (Exception e) {
            return Result.fail("切换数据源失败：" + e.getMessage());
        }
    }
}