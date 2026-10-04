package org.example.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.example.service.GlobalNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GlobalNotificationServiceImpl implements GlobalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(GlobalNotificationServiceImpl.class);
    private static final String NOTIF_PREFIX = "global:notif:";
    private static final String GLOBAL_ZSET = "global:notif:all";
    private static final String USER_ZSET_PREFIX = "global:notif:user:";
    private static final String COUNTER_KEY = "global:notif:counter";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate notifRedis;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GlobalNotificationServiceImpl(
            @Qualifier("globalNotificationRedisTemplate") StringRedisTemplate notifRedis,
            SysUserMapper sysUserMapper) {
        this.notifRedis = notifRedis;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Result sendNotification(String sender, String receivers, String type, String title, String content, long expireSeconds) {
        if (title == null || title.isBlank()) return Result.fail("标题不能为空");
        if (type == null || type.isBlank()) type = "INFO";
        if (expireSeconds <= 0) expireSeconds = 86400;

        Long idLong = notifRedis.opsForValue().increment(COUNTER_KEY);
        String id = String.valueOf(idLong);
        String now = LocalDateTime.now().format(DT_FMT);
        String expireAt = LocalDateTime.now().plusSeconds(expireSeconds).format(DT_FMT);

        Map<String, String> notif = new LinkedHashMap<>();
        notif.put("id", id);
        notif.put("title", title.trim());
        notif.put("content", content != null ? content.trim() : "");
        notif.put("type", type);
        notif.put("sender", sender);
        notif.put("receivers", receivers != null ? receivers : "ALL");
        notif.put("createdAt", now);
        notif.put("expireAt", expireAt);

        String key = NOTIF_PREFIX + id;
        try {
            String json = objectMapper.writeValueAsString(notif);
            notifRedis.opsForValue().set(key, json, Duration.ofSeconds(expireSeconds));
        } catch (Exception e) {
            log.error("Redis写入通知失败: {}", e.getMessage());
            return Result.fail("通知存储失败");
        }

        double score = System.currentTimeMillis();
        notifRedis.opsForZSet().add(GLOBAL_ZSET, id, score);

        cleanZSet(GLOBAL_ZSET);

        if ("ALL".equalsIgnoreCase(receivers)) {
            List<SysUser> allUsers = sysUserMapper.findAll();
            for (SysUser u : allUsers) {
                if (u.getName().equals(sender)) continue;
                notifRedis.opsForZSet().add(USER_ZSET_PREFIX + u.getName(), id, score);
            }
        } else {
            for (String r : receivers.split(",")) {
                String trimmed = r.trim();
                if (trimmed.isEmpty() || trimmed.equals(sender)) continue;
                notifRedis.opsForZSet().add(USER_ZSET_PREFIX + trimmed, id, score);
            }
        }

        return Result.success("通知已推送");
    }

    @Override
    public Result getGlobalHistory(int page, int size) {
        long total = cleanAndCount(GLOBAL_ZSET);
        List<Map<String, String>> list = getPage(GLOBAL_ZSET, page, size);
        return Result.success("查询成功", list, null, total, page, size);
    }

    @Override
    public Result getUserNotifications(String userName, int page, int size) {
        String userZSet = USER_ZSET_PREFIX + userName;
        long total = cleanAndCount(userZSet);
        List<Map<String, String>> list = getPage(userZSet, page, size);
        return Result.success("查询成功", list, null, total, page, size);
    }

    @Override
    public Result deleteNotification(String id) {
        notifRedis.delete(NOTIF_PREFIX + id);
        notifRedis.opsForZSet().remove(GLOBAL_ZSET, id);
        return Result.success("已删除");
    }

    @Override
    public Result pollNew(String userName, long since) {
        String userZSet = USER_ZSET_PREFIX + userName;
        Set<String> ids = notifRedis.opsForZSet().rangeByScore(userZSet, since + 1, Double.MAX_VALUE);
        List<Map<String, String>> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.success("无新通知", result);

        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> notif = readNotif(id);
            if (notif == null) {
                toRemove.add(id);
            } else {
                result.add(notif);
            }
        }
        if (!toRemove.isEmpty()) {
            notifRedis.opsForZSet().remove(userZSet, toRemove.toArray());
        }
        return Result.success("查询成功", result);
    }

    private List<Map<String, String>> getPage(String zSetKey, int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1;
        Set<String> ids = notifRedis.opsForZSet().reverseRange(zSetKey, start, end);
        List<Map<String, String>> result = new ArrayList<>();
        if (ids == null) return result;

        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> notif = readNotif(id);
            if (notif == null) {
                toRemove.add(id);
            } else {
                result.add(notif);
            }
        }
        for (String id : toRemove) {
            notifRedis.opsForZSet().remove(zSetKey, id);
        }
        return result;
    }

    private long cleanAndCount(String zSetKey) {
        Set<String> ids = notifRedis.opsForZSet().range(zSetKey, 0, -1);
        if (ids == null) return 0;
        long count = 0;
        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            if (Boolean.TRUE.equals(notifRedis.hasKey(NOTIF_PREFIX + id))) {
                count++;
            } else {
                toRemove.add(id);
            }
        }
        if (!toRemove.isEmpty()) {
            notifRedis.opsForZSet().remove(zSetKey, toRemove.toArray());
        }
        return count;
    }

    private void cleanZSet(String zSetKey) {
        Set<String> ids = notifRedis.opsForZSet().range(zSetKey, 0, -1);
        if (ids == null) return;
        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            if (Boolean.FALSE.equals(notifRedis.hasKey(NOTIF_PREFIX + id))) {
                toRemove.add(id);
            }
        }
        if (!toRemove.isEmpty()) {
            notifRedis.opsForZSet().remove(zSetKey, toRemove.toArray());
        }
    }

    private Map<String, String> readNotif(String id) {
        String json = notifRedis.opsForValue().get(NOTIF_PREFIX + id);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("通知JSON解析失败 id={}: {}", id, e.getMessage());
            return null;
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}