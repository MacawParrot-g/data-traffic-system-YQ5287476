package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.common.UserContext;
import org.example.service.DedupSessionService;
import org.example.util.SysUserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UserContextInterceptor implements HandlerInterceptor {

    private final DedupSessionService dedupSessionService;
    private final RedisTemplate<String, Object> kickRedisTemplate;

    public UserContextInterceptor(DedupSessionService dedupSessionService,
                                  RedisTemplate<String, Object> kickRedisTemplate) {
        this.dedupSessionService = dedupSessionService;
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String username = request.getHeader("X-User-Name");
        if (username != null && !username.isBlank()) {
            try {
                username = URLDecoder.decode(username, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        } else {
            username = "无用户参数";
        }
        UserContext.setUsername(username);

        String token = SysUserService.getTokenFromCookie(request);
        if (token != null) {
            Map<Object, Object> data = kickRedisTemplate.opsForHash().entries("auth:token:" + token);
            if (data != null && data.containsKey("type")) {
                UserContext.setUserType(data.get("type").toString());
            }
        }

        if (!"无用户参数".equals(username)) {
            dedupSessionService.checkAndRegenerateSession(username);
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        UserContext.setIp(ip);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
