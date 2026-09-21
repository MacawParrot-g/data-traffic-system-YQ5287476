//package org.example.util;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpSession;
//import org.example.common.Result;
//import org.example.entity.SysUser;
//import org.example.mapper.SysUserMapper;
//import org.example.service.UserManageService;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.stereotype.Service;
//
//import java.security.SecureRandom;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class SysUserService {
//
//    private SysUserMapper sysUserMapper;
//    private RedisTemplate<String, Object> kickRedisTemplate;
//    private UserManageService userManageService;
//    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
//    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//    private static final SecureRandom RANDOM = new SecureRandom();
//
//    public SysUserService(SysUserMapper sysUserMapper,
//                          RedisTemplate<String, Object> kickRedisTemplate,
//                          UserManageService userManageService) {
//        this.sysUserMapper = sysUserMapper;
//        this.kickRedisTemplate = kickRedisTemplate;
//        this.userManageService = userManageService;
//    }
//
//    public Result login(String uid, String rawPwd, HttpServletRequest request) {
//        if (uid == null || uid.isBlank() || rawPwd == null || rawPwd.isBlank()) {
//            return Result.fail("账号和密码不能为空");
//        }
//        String banKey = "ban:uid:" + uid.trim();
//        if (Boolean.TRUE.equals(kickRedisTemplate.hasKey(banKey))) {
//            Long ttl = kickRedisTemplate.getExpire(banKey, TimeUnit.SECONDS);
//            return Result.fail("账号已被封禁，请 " + (ttl != null ? ttl : "") + " 秒后重试");
//        }
//        SysUser user = sysUserMapper.findByUid(uid.trim());
//        if (user == null) {
//            return Result.fail("账号不存在");
//        }
//        if (!passwordEncoder.matches(rawPwd, user.getPwd())) {
//            return Result.fail("密码错误");
//        }
//        HttpSession session = request.getSession(true);
//        session.setAttribute("uid", user.getUid());
//        session.setAttribute("name", user.getName());
//        session.setAttribute("type", user.getType());
//        session.setMaxInactiveInterval(30 * 24 * 60 * 60);
//        kickRedisTemplate.opsForValue().set("session:uid:" + user.getUid(), session.getId(), 30, TimeUnit.DAYS);
//        userManageService.registerSession(user.getUid(), session);
//        Map<String, Object> data = new HashMap<>();
//        data.put("uid", user.getUid());
//        data.put("name", user.getName());
//        data.put("type", user.getType());
//        return Result.success("登录成功", data);
//    }
//
//    public Result logout(HttpServletRequest request) {
//        HttpSession session = request.getSession(false);
//        if (session != null) {
//            Object uid = session.getAttribute("uid");
//            if (uid != null) {
//                kickRedisTemplate.delete("session:uid:" + uid);
//                userManageService.unregisterSession(uid.toString());
//            }
//            session.invalidate();
//        }
//        return Result.success("已退出登录");
//    }
//
//    public Result getLoginStatus(HttpServletRequest request) {
//        HttpSession session = request.getSession(false);
//        if (session != null && session.getAttribute("uid") != null) {
//            Map<String, Object> data = new HashMap<>();
//            data.put("uid", session.getAttribute("uid"));
//            data.put("name", session.getAttribute("name"));
//            data.put("type", session.getAttribute("type"));
//            return Result.success("已登录", Map.of("loggedIn", true, "data", data));
//        }
//        return Result.success("未登录", Map.of("loggedIn", false));
//    }
//
//    public Result createUser(String name, String rawPwd, String type, HttpServletRequest request) {
//        String operatorType = getOperatorType(request);
//        if (operatorType == null) {
//            return Result.fail("未登录");
//        }
//        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
//            return Result.fail("仅管理员可创建用户");
//        }
//        if (name == null || name.isBlank() || rawPwd == null || rawPwd.isBlank()) {
//            return Result.fail("姓名和密码不能为空");
//        }
//        String uid = generateUid();
//        while (sysUserMapper.existsByUid(uid) > 0) {
//            uid = generateUid();
//        }
//        SysUser user = new SysUser();
//        user.setUid(uid);
//        user.setName(name.trim());
//        user.setPwd(passwordEncoder.encode(rawPwd));
//        user.setType(type != null ? type : "USER");
//        sysUserMapper.insertUser(user);
//        Map<String, Object> data = new HashMap<>();
//        data.put("uid", user.getUid());
//        data.put("name", user.getName());
//        data.put("type", user.getType());
//        return Result.success("用户创建成功", data);
//    }
//
//    public Result deleteUser(String uid, HttpServletRequest request) {
//        String operatorType = getOperatorType(request);
//        if (operatorType == null) {
//            return Result.fail("未登录");
//        }
//        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
//            return Result.fail("仅管理员可删除用户");
//        }
//        SysUser user = sysUserMapper.findByUid(uid);
//        if (user == null) {
//            return Result.fail("用户不存在");
//        }
//        sysUserMapper.deleteByUid(uid);
//        return Result.success("用户已删除");
//    }
//
//    public Result listUsers(HttpServletRequest request) {
//        String operatorType = getOperatorType(request);
//        if (operatorType == null) {
//            return Result.fail("未登录");
//        }
//        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
//            return Result.fail("仅管理员可查看用户列表");
//        }
//        List<SysUser> users = sysUserMapper.findAll();
//        List<Map<String, Object>> result = new ArrayList<>();
//        for (SysUser u : users) {
//            Map<String, Object> map = new HashMap<>();
//            map.put("uid", u.getUid());
//            map.put("name", u.getName());
//            map.put("type", u.getType());
//            map.put("online", userManageService.isOnline(u.getUid()));
//            result.add(map);
//        }
//        return Result.success("查询成功", result);
//    }
//
//    private String getOperatorType(HttpServletRequest request) {
//        HttpSession session = request.getSession(false);
//        if (session == null || session.getAttribute("uid") == null) {
//            return null;
//        }
//        return (String) session.getAttribute("type");
//    }
//    private String generateUid() {
//        StringBuilder sb = new StringBuilder("U");
//        for (int i = 0; i < 10; i++) {
//            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
//        }
//        return sb.toString();
//    }
//}

package org.example.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.common.Result;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.example.service.UserManageService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SysUserService {

    private SysUserMapper sysUserMapper;
    private RedisTemplate<String, Object> kickRedisTemplate;
    private UserManageService userManageService;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AUTH_TOKEN_PREFIX = "auth:token:";
    private static final String AUTH_UID_PREFIX = "auth:uid:";
    private static final long AUTH_TOKEN_TTL_DAYS = 30;

    public SysUserService(SysUserMapper sysUserMapper,
                          RedisTemplate<String, Object> kickRedisTemplate,
                          UserManageService userManageService) {
        this.sysUserMapper = sysUserMapper;
        this.kickRedisTemplate = kickRedisTemplate;
        this.userManageService = userManageService;
    }

    public Result login(String uid, String rawPwd, HttpServletRequest request, HttpServletResponse response) {
        if (uid == null || uid.isBlank() || rawPwd == null || rawPwd.isBlank()) {
            return Result.fail("账号和密码不能为空");
        }
        String banKey = "ban:uid:" + uid.trim();
        if (Boolean.TRUE.equals(kickRedisTemplate.hasKey(banKey))) {
            Long ttl = kickRedisTemplate.getExpire(banKey, TimeUnit.SECONDS);
            return Result.fail("账号已被封禁，请 " + (ttl != null ? ttl : "") + " 秒后重试");
        }
        SysUser user = sysUserMapper.findByUid(uid.trim());
        if (user == null) {
            return Result.fail("账号不存在");
        }
        if (!passwordEncoder.matches(rawPwd, user.getPwd())) {
            return Result.fail("密码错误");
        }
        String oldToken = (String) kickRedisTemplate.opsForValue().get(AUTH_UID_PREFIX + user.getUid());
        if (oldToken != null) {
            kickRedisTemplate.delete(AUTH_TOKEN_PREFIX + oldToken);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("uid", user.getUid());
        sessionData.put("name", user.getName());
        sessionData.put("type", user.getType());
        kickRedisTemplate.opsForValue().set(AUTH_TOKEN_PREFIX + token, sessionData, AUTH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
        kickRedisTemplate.opsForValue().set(AUTH_UID_PREFIX + user.getUid(), token, AUTH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
        Cookie cookie = new Cookie("AUTH_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (AUTH_TOKEN_TTL_DAYS * 24 * 60 * 60));
        response.addCookie(cookie);
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", user.getName());
        data.put("type", user.getType());
        return Result.success("登录成功", data);
    }

    public Result logout(HttpServletRequest request, HttpServletResponse response) {
        String token = getTokenFromCookie(request);
        if (token != null) {
            Map<Object, Object> data = kickRedisTemplate.opsForHash().entries(AUTH_TOKEN_PREFIX + token);
            if (data != null && data.containsKey("uid")) {
                kickRedisTemplate.delete(AUTH_UID_PREFIX + data.get("uid"));
            }
            kickRedisTemplate.delete(AUTH_TOKEN_PREFIX + token);
        }
        Cookie cookie = new Cookie("AUTH_TOKEN", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Result.success("已退出登录");
    }

    public Result getLoginStatus(HttpServletRequest request) {
        String token = getTokenFromCookie(request);
        if (token != null) {
            Map<Object, Object> data = kickRedisTemplate.opsForHash().entries(AUTH_TOKEN_PREFIX + token);
            if (data != null && data.containsKey("uid")) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("uid", data.get("uid"));
                userInfo.put("name", data.get("name"));
                userInfo.put("type", data.get("type"));
                return Result.success("已登录", Map.of("loggedIn", true, "data", userInfo));
            }
        }
        return Result.success("未登录", Map.of("loggedIn", false));
    }

    public Result createUser(String name, String rawPwd, String type, HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
            return Result.fail("仅管理员可创建用户");
        }
        if (name == null || name.isBlank() || rawPwd == null || rawPwd.isBlank()) {
            return Result.fail("姓名和密码不能为空");
        }
        String uid = generateUid();
        while (sysUserMapper.existsByUid(uid) > 0) {
            uid = generateUid();
        }
        SysUser user = new SysUser();
        user.setUid(uid);
        user.setName(name.trim());
        user.setPwd(passwordEncoder.encode(rawPwd));
        user.setType(type != null ? type : "USER");
        sysUserMapper.insertUser(user);
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", user.getName());
        data.put("type", user.getType());
        return Result.success("用户创建成功", data);
    }

    public Result deleteUser(String uid, HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
            return Result.fail("仅管理员可删除用户");
        }
        SysUser user = sysUserMapper.findByUid(uid);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        sysUserMapper.deleteByUid(uid);
        return Result.success("用户已删除");
    }

    public Result listUsers(HttpServletRequest request) {
        String operatorType = getOperatorType(request);
        if (operatorType == null) {
            return Result.fail("未登录");
        }
        if (!"ADMIN".equals(operatorType) && !"DEVELOPER".equals(operatorType)) {
            return Result.fail("仅管理员可查看用户列表");
        }
        List<SysUser> users = sysUserMapper.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("uid", u.getUid());
            map.put("name", u.getName());
            map.put("type", u.getType());
            map.put("online", userManageService.isOnline(u.getUid()));
            result.add(map);
        }
        return Result.success("查询成功", result);
    }

    private String getOperatorType(HttpServletRequest request) {
        String token = getTokenFromCookie(request);
        if (token == null) return null;
        Map<Object, Object> data = kickRedisTemplate.opsForHash().entries(AUTH_TOKEN_PREFIX + token);
        if (data == null || !data.containsKey("type")) return null;
        return data.get("type").toString();
    }

    public static String getTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if ("AUTH_TOKEN".equals(c.getName())) {
                String val = c.getValue();
                if (val != null && !val.isBlank()) return val;
            }
        }
        return null;
    }

    private String generateUid() {
        StringBuilder sb = new StringBuilder("U");
        for (int i = 0; i < 10; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
