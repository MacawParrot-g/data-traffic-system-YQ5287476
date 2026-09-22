package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.config.ShutdownNotifier;
import org.example.util.SysUserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final ShutdownNotifier shutdownNotifier;
    private final RedisTemplate<String, Object> kickRedisTemplate;

    public SseController(ShutdownNotifier shutdownNotifier,
                         @Qualifier("kickRedisTemplate") RedisTemplate<String, Object> kickRedisTemplate) {
        this.shutdownNotifier = shutdownNotifier;
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @GetMapping(value = "/connect", produces = "text/event-stream")
    public SseEmitter connect(HttpServletRequest request) {
        String token = SysUserService.getTokenFromCookie(request);
        if (token == null) {
            throw new RuntimeException("未登录");
        }
        Map<Object, Object> data = kickRedisTemplate.opsForHash().entries("auth:token:" + token);
        if (data == null || !data.containsKey("name")) {
            throw new RuntimeException("未登录");
        }
        String userName = data.get("name").toString();
        return shutdownNotifier.createEmitterForUser(userName);
    }
}
