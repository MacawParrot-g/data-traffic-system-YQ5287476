package org.example.service;

import org.example.common.Result;

import java.util.List;

public interface AlertNotificationService {
    Result sendAlert(String receivers, String type, String title, String content, String sender);
    Result getSentAlerts(String sender, int page, int size);
    Result acknowledgeAlert(String receiver, String title);
    Result deleteAlert(Long id);
    Result getUnacknowledged(String receiver);
}
