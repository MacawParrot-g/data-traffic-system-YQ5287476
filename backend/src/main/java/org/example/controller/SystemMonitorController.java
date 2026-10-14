package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.SystemMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemMonitorController {

    @Autowired
    private SystemMonitorService systemMonitorService;

    @GetMapping("/info")
    public Result getSystemInfo() {
        return systemMonitorService.getSystemInfo();
    }

    @GetMapping("/redis/info")
    public Result getRedisInfo() {
        return systemMonitorService.getRedisInfo();
    }

    @GetMapping("/redis/keys")
    @LogExecutionTime("Redis键查询")
    public Result getRedisKeys(@RequestParam(defaultValue = "0") int db,
                               @RequestParam(defaultValue = "100") int limit) {
        return systemMonitorService.getRedisKeys(db, limit);
    }

    @GetMapping("/log/search")
    public Result searchLogs(@RequestParam(defaultValue = "500") int lines,
                             @RequestParam(defaultValue = "ALL") String level,
                             @RequestParam(required = false) String user,
                             @RequestParam(required = false) String function,
                             @RequestParam(required = false) String clazz,
                             @RequestParam(required = false) String method,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Integer minDuration,
                             @RequestParam(required = false) Integer maxDuration) {
        return systemMonitorService.searchLogs(lines, level, user, function, clazz, method, status, minDuration, maxDuration);
    }

    @GetMapping("/log/tail")
    public Result tailLog(@RequestParam(defaultValue = "200") int lines,
                          @RequestParam(defaultValue = "ALL") String level) {
        return systemMonitorService.tailLog(lines, level);
    }

    @PostMapping("/terminal/exec")
    @LogExecutionTime("终端命令执行")
    public Result terminalExec(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        return systemMonitorService.executeCommand(command);
    }
}
