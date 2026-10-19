package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.service.MySQLBackupMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mysql")
public class MySQLBackupMonitorController {

    @Autowired
    private MySQLBackupMonitorService mysqlBackupMonitorService;

    @GetMapping("/monitor")
    @LogExecutionTime("MySQL监控状态查询")
    public Result monitor() {
        return mysqlBackupMonitorService.getMonitorStatus();
    }

    @PostMapping("/backup")
    @LogExecutionTime("MySQL手动备份")
    public Result backup() {
        return mysqlBackupMonitorService.manualBackup();
    }

    @GetMapping("/backups")
    public Result backups() {
        return mysqlBackupMonitorService.listBackups();
    }
}
