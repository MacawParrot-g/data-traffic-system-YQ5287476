package org.example.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.common.Result;

public interface UserManageService {
    Result listOnlineUsers(HttpServletRequest request);
    Result kickUser(String uid, Integer banSeconds, HttpServletRequest request);
    Result resetPassword(String uid, String newPwd, HttpServletRequest request);
    boolean isOnline(String uid);
}
