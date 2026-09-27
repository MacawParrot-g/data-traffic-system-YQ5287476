// 新文件: C:\Users\EDY\data-traffic-system-YQ5287476\backend\src\main\java\org\example\service\impl\GlobalNotificationServiceImpl.java
package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.Result;
import org.example.config.ShutdownNotifier;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.example.service.GlobalNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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

    private final RedisTemplate<String, Object> notifRedis;
    private final ShutdownNotifier shutdownNotifier;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GlobalNotificationServiceImpl(
            @Qualifier("globalNotificationRedisTemplate") RedisTemplate<String, Object> notifRedis,
            ShutdownNotifier shutdownNotifier,
            SysUserMapper sysUserMapper) {
        this.notifRedis = notifRedis;
        this.shutdownNotifier = shutdownNotifier;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Result sendNotification(String sender, String receivers, String type, String title, String content, long expireSeconds) {
        if (title == null || title.isBlank()) return Result.fail("标题不能为空");
        if (type == null || type.isBlank()) type = "INFO";
        if (expireSeconds <= 0) expireSeconds = 86400;

        long id = notifRedis.opsForValue().increment(COUNTER_KEY);
        String now = LocalDateTime.now().format(DT_FMT);
        String expireAt = LocalDateTime.now().plusSeconds(expireSeconds).format(DT_FMT);

        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("id", String.valueOf(id));
        notif.put("title", title.trim());
        notif.put("content", content != null ? content.trim() : "");
        notif.put("type", type);
        notif.put("sender", sender);
        notif.put("receivers", receivers != null ? receivers : "ALL");
        notif.put("createdAt", now);
        notif.put("expireAt", expireAt);

        String key = NOTIF_PREFIX + id;
        try {
            notifRedis.opsForHash().putAll(key, notif);
            notifRedis.expire(key, expireSeconds);
        } catch (Exception e) {
            log.error("Redis写入通知失败: {}", e.getMessage());
            return Result.fail("通知存储失败");
        }

        double score = System.currentTimeMillis();
        notifRedis.opsForZSet().add(GLOBAL_ZSET, String.valueOf(id), score);

        cleanExpiredFromZSet(GLOBAL_ZSET);

        String sseData;
        try {
            sseData = objectMapper.writeValueAsString(notif);
        } catch (Exception e) {
            sseData = "{}";
        }

        if ("ALL".equalsIgnoreCase(receivers)) {
            List<SysUser> allUsers = sysUserMapper.findAll();
            for (SysUser u : allUsers) {
                if (u.getName().equals(sender)) continue;
                notifRedis.opsForZSet().add(USER_ZSET_PREFIX + u.getName(), String.valueOf(id), score);
                shutdownNotifier.sendToUser(u.getName(), "global_notification", sseData);
            }
        } else {
            for (String r : receivers.split(",")) {
                String trimmed = r.trim();
                if (trimmed.isEmpty() || trimmed.equals(sender)) continue;
                notifRedis.opsForZSet().add(USER_ZSET_PREFIX + trimmed, String.valueOf(id), score);
                shutdownNotifier.sendToUser(trimmed, "global_notification", sseData);
            }
        }

        return Result.success("通知已推送");
    }

    @Override
    public Result getGlobalHistory(int page, int size) {
        long total = cleanAndCount(GLOBAL_ZSET);
        List<Map<String, Object>> list = getNotifPage(GLOBAL_ZSET, page, size);
        return Result.success("查询成功", list, null, total, page, size);
    }

    @Override
    public Result getUserNotifications(String userName, int page, int size) {
        String userZSet = USER_ZSET_PREFIX + userName;
        long total = cleanAndCount(userZSet);
        if (total == 0) {
            total = cleanAndCount(GLOBAL_ZSET);
            List<Map<String, Object>> list = getNotifPage(GLOBAL_ZSET, page, size);
            return Result.success("查询成功", list, null, total, page, size);
        }
        List<Map<String, Object>> list = getNotifPage(userZSet, page, size);
        return Result.success("查询成功", list, null, total, page, size);
    }

    @Override
    public Result deleteNotification(String id) {
        String key = NOTIF_PREFIX + id;
        notifRedis.delete(key);
        notifRedis.opsForZSet().remove(GLOBAL_ZSET, id);
        return Result.success("已删除");
    }

    @Override
    public Result pollNew(long since) {
        Set<String> ids = notifRedis.opsForZSet().rangeByScore(GLOBAL_ZSET, since + 1, Double.MAX_VALUE);
        List<Map<String, Object>> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return Result.success("无新通知", result);

        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> hash = notifRedis.opsForHash().entries(NOTIF_PREFIX + id);
            if (hash.isEmpty()) {
                toRemove.add(id);
                continue;
            }
            result.add(new LinkedHashMap<>(hash));
        }
        if (!toRemove.isEmpty()) {
            notifRedis.opsForZSet().remove(GLOBAL_ZSET, toRemove.toArray());
        }
        return Result.success("查询成功", result);
    }

    private List<Map<String, Object>> getNotifPage(String zSetKey, int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1;
        Set<String> ids = notifRedis.opsForZSet().reverseRange(zSetKey, start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        if (ids == null) return result;
        List<String> toRemove = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> hash = notifRedis.opsForHash().entries(NOTIF_PREFIX + id);
            if (hash.isEmpty()) {
                toRemove.add(id);
                continue;
            }
            result.add(new LinkedHashMap<>(hash));
        }
        for (String id : toRemove) {
            notifRedis.opsForZSet().remove(zSetKey, id);
        }
        return result;
    }

    private long cleanAndCount(String zSetKey) {
        Set<ZSetOperations.TypedTuple<Object>> tuples = notifRedis.opsForZSet().rangeWithScores(zSetKey, 0, -1);
        if (tuples == null) return 0;
        long count = 0;
        List<String> toRemove = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> t : tuples) {
            String id = String.valueOf(t.getValue());
            if (Boolean.FALSE.equals(notifRedis.hasKey(NOTIF_PREFIX + id))) {
                toRemove.add(id);
            } else {
                count++;
            }
        }
        if (!toRemove.isEmpty()) {
            notifRedis.opsForZSet().remove(zSetKey, toRemove.toArray());
        }
        return count;
    }

    private void cleanExpiredFromZSet(String zSetKey) {
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
}
