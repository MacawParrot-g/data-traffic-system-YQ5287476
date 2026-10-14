package org.example.service;

import org.example.common.Result;

public interface SystemMonitorService {

    Result getSystemInfo();

    Result getRedisInfo();

    Result tailLog(int lines, String level);

    Result searchLogs(int lines, String level, String user, String function, String clazz, String method, String status, Integer minDuration, Integer maxDuration);

    Result getRedisKeys(int db, int limit);

    Result executeCommand(String command);
}