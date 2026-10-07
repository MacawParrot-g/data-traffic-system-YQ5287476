// 新文件: C:\Users\EDY\data-traffic-system-YQ5287476\backend\src\main\java\org\example\controller\GlobalNotificationController.java
package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.annotation.LogExecutionTime;
import org.example.annotation.SkipRateLimit;
import org.example.common.Result;
import org.example.service.GlobalNotificationService;
import org.example.util.SysUserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/global-notification")
public class GlobalNotificationController {

    private final GlobalNotificationService globalNotificationService;
    private final RedisTemplate<String, Object> kickRedisTemplate;

    public GlobalNotificationController(
            GlobalNotificationService globalNotificationService,
            @Qualifier("kickRedisTemplate") RedisTemplate<String, Object> kickRedisTemplate) {
        this.globalNotificationService = globalNotificationService;
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @PostMapping("/send")
    @LogExecutionTime("发送全域通知")
    public Result send(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String sender = getUserName(request);
        if (sender == null) return Result.fail("未登录");
        String receivers = (String) body.getOrDefault("receivers", "ALL");
        String type = (String) body.getOrDefault("type", "INFO");
        String title = (String) body.get("title");
        String content = (String) body.getOrDefault("content", "");
        long expireSeconds = body.containsKey("expireSeconds")
                ? ((Number) body.get("expireSeconds")).longValue() : 86400L;
        return globalNotificationService.sendNotification(sender, receivers, type, title, content, expireSeconds);
    }

    @GetMapping("/history")
    @LogExecutionTime("查询全域通知历史")
    public Result history(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return globalNotificationService.getGlobalHistory(page, size);
    }

    @GetMapping("/my")
    @SkipRateLimit
    public Result myNotifications(HttpServletRequest request,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        String userName = getUserName(request);
        if (userName == null) return Result.fail("未登录");
        return globalNotificationService.getUserNotifications(userName, page, size);
    }

    @DeleteMapping("/delete")
    @LogExecutionTime("删除全域通知")
    public Result delete(@RequestParam String id) {
        return globalNotificationService.deleteNotification(id);
    }

    @GetMapping("/poll")
    @SkipRateLimit
    public Result poll(HttpServletRequest request, @RequestParam(defaultValue = "0") long since) {
        String userName = getUserName(request);
        if (userName == null) return Result.fail("未登录");
        return globalNotificationService.pollNew(userName, since);
    }

    private String getUserName(HttpServletRequest request) {
        String token = SysUserService.getTokenFromCookie(request);
        if (token == null) return null;
        Map<Object, Object> data = kickRedisTemplate.opsForHash().entries("auth:token:" + token);
        if (data == null || !data.containsKey("name")) return null;
        return data.get("name").toString();
    }
}
