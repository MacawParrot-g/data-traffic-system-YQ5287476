//package org.example.config;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import jakarta.servlet.http.HttpSession;
//import org.springframework.web.servlet.HandlerInterceptor;
//
//public class AuthInterceptor implements HandlerInterceptor {
//
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
//            return true;
//        }
//        HttpSession session = request.getSession(false);
//        if (session != null && session.getAttribute("uid") != null) {
//            return true;
//        }
//        response.setStatus(401);
//        response.setContentType("application/json;charset=UTF-8");
//        response.getWriter().write("{\"success\":false,\"message\":\"未登录，请重新登录\"}");
//        return false;
//    }
//}

package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.util.SysUserService;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

public class AuthInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private final RedisTemplate<String, Object> kickRedisTemplate;

    public AuthInterceptor(RedisTemplate<String, Object> kickRedisTemplate) {
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = SysUserService.getTokenFromCookie(request);
        if (token != null) {
            Map<Object, Object> data = kickRedisTemplate.opsForHash().entries("auth:token:" + token);
            if (data != null && data.containsKey("uid")) {
                return true;
            }
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"未登录，请重新登录\"}");
        return false;
    }
}
