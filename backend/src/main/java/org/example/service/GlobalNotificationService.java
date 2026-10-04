package org.example.service;

import org.example.common.Result;

public interface GlobalNotificationService {
    Result sendNotification(String sender, String receivers, String type, String title, String content, long expireSeconds);
    Result getGlobalHistory(int page, int size);
    Result getUserNotifications(String userName, int page, int size);
    Result deleteNotification(String id);
    Result pollNew(String userName, long since);
}
