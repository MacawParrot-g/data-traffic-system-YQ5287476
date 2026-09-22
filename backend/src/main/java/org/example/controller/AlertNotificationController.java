package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.AlertNotificationService;
import org.example.util.SysUserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertNotificationController {

    private final AlertNotificationService alertNotificationService;
    private final RedisTemplate<String, Object> kickRedisTemplate;

    public AlertNotificationController(AlertNotificationService alertNotificationService,
                                       @Qualifier("kickRedisTemplate") RedisTemplate<String, Object> kickRedisTemplate) {
        this.alertNotificationService = alertNotificationService;
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @PostMapping("/send")
    @LogExecutionTime("发送强制通知")
    public Result send(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String sender = getUserName(request);
        if (sender == null) return Result.fail("未登录");
        return alertNotificationService.sendAlert(
                body.get("receivers"),
                body.get("type"),
                body.get("title"),
                body.get("content"),
                sender
        );
    }

    @GetMapping("/list")
    @LogExecutionTime("查询已发通知")
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "15") int size,
                       HttpServletRequest request) {
        String sender = getUserName(request);
        if (sender == null) return Result.fail("未登录");
        return alertNotificationService.getSentAlerts(sender, page, size);
    }

    @PostMapping("/ack")
    public Result ack(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String receiver = getUserName(request);
        if (receiver == null) return Result.fail("未登录");
        return alertNotificationService.acknowledgeAlert(receiver, body.get("title"));
    }

    @DeleteMapping("/delete")
    @LogExecutionTime("删除通知")
    public Result delete(@RequestParam Long id) {
        return alertNotificationService.deleteAlert(id);
    }

    private String getUserName(HttpServletRequest request) {
        String token = SysUserService.getTokenFromCookie(request);
        if (token == null) return null;
        Map<Object, Object> data = kickRedisTemplate.opsForHash().entries("auth:token:" + token);
        if (data == null || !data.containsKey("name")) return null;
        return data.get("name").toString();
    }
}
