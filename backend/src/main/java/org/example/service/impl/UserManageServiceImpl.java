package org.example.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.example.common.Result;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.example.service.UserManageService;
import org.example.util.SysUserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserManageServiceImpl implements UserManageService {

    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> kickRedisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String AUTH_TOKEN_PREFIX = "auth:token:";
    private static final String AUTH_UID_PREFIX = "auth:uid:";

    public UserManageServiceImpl(SysUserMapper sysUserMapper,
                                 @Qualifier("kickRedisTemplate") RedisTemplate<String, Object> kickRedisTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.kickRedisTemplate = kickRedisTemplate;
    }

    @Override
    public Result listOnlineUsers(HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
            return Result.fail("仅管理员可查看");
        }
        List<SysUser> users = sysUserMapper.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("uid", u.getUid());
            map.put("name", u.getName());
            map.put("type", u.getType());
            map.put("online", isOnline(u.getUid()));
            result.add(map);
        }
        return Result.success("查询成功", result);
    }

    @Override
    public boolean isOnline(String uid) {
        String uidKey = AUTH_UID_PREFIX + uid;
        Long size = kickRedisTemplate.opsForList().size(uidKey);
        if (size == null || size == 0) return false;
        List<Object> tokens = kickRedisTemplate.opsForList().range(uidKey, 0, -1);
        if (tokens == null || tokens.isEmpty()) return false;
        for (Object t : tokens) {
            Boolean exists = kickRedisTemplate.hasKey(AUTH_TOKEN_PREFIX + t);
            if (Boolean.TRUE.equals(exists)) return true;
        }
        return false;
    }

    @Override
    public Result kickUser(String uid, Integer banSeconds, HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
            return Result.fail("仅管理员可踢人");
        }
        SysUser user = sysUserMapper.findByUid(uid);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        invalidateSession(uid);
        if (banSeconds != null && banSeconds > 0) {
            kickRedisTemplate.opsForValue().set("ban:uid:" + uid, "1", banSeconds, TimeUnit.SECONDS);
        }
        return Result.success("已踢下线" + (banSeconds != null && banSeconds > 0 ? "，封禁 " + banSeconds + " 秒" : ""));
    }

    @Override
    public Result resetPassword(String uid, String newPwd, HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType))  {
            return Result.fail("仅管理员可重置密码");
        }
        if (newPwd == null || newPwd.isBlank()) {
            return Result.fail("新密码不能为空");
        }
        SysUser user = sysUserMapper.findByUid(uid);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        sysUserMapper.updatePassword(uid, passwordEncoder.encode(newPwd));
        invalidateSession(uid);
        return Result.success("密码已重置，用户已被踢下线");
    }

    private boolean isSessionAlive(String uid) {
        return isOnline(uid);
    }

    private void invalidateSession(String uid) {
        String uidKey = AUTH_UID_PREFIX + uid;
        List<Object> tokens = kickRedisTemplate.opsForList().range(uidKey, 0, -1);
        if (tokens != null) {
            for (Object t : tokens) {
                kickRedisTemplate.delete(AUTH_TOKEN_PREFIX + t);
            }
        }
        kickRedisTemplate.delete(uidKey);
    }

    private String getOperatorType(HttpServletRequest request) {
        String token = SysUserService.getTokenFromCookie(request);
        if (token == null) return null;
        Map<Object, Object> data = kickRedisTemplate.opsForHash().entries(AUTH_TOKEN_PREFIX + token);
        if (data == null || !data.containsKey("type")) return null;
        return (String) data.get("type");
    }
}
