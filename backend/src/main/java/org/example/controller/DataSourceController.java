package org.example.controller;

import org.example.annotation.LogExecutionTime;
import org.example.common.Result;
import org.example.common.UserContext;
import org.example.service.DataSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/datasource")
public class DataSourceController {

    @Autowired
    private DataSourceService dataSourceService;

    @GetMapping("/config")
    @LogExecutionTime("查询当前用户数据源配置")
    public Result getConfig() {
        String username = UserContext.getUsername();
        return dataSourceService.getConfig(username);
    }

    @PostMapping("/config")
    @LogExecutionTime("保存用户数据源配置")
    public Result saveConfig(@RequestBody Map<String, String> body) {
        String username = UserContext.getUsername();
        String url = body.get("url");
        String dsUsername = body.get("username");
        String password = body.get("password");
        return dataSourceService.saveConfig(username, url, dsUsername, password);
    }

    @DeleteMapping("/config")
    @LogExecutionTime("删除用户数据源配置")
    public Result deleteConfig() {
        String username = UserContext.getUsername();
        return dataSourceService.deleteConfig(username);
    }

    @PostMapping("/test")
    @LogExecutionTime("测试数据源连接")
    public Result testConnection(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String dsUsername = body.get("username");
        String password = body.get("password");
        return dataSourceService.testConnection(url, dsUsername, password);
    }

    @GetMapping("/status")
    @LogExecutionTime("查询数据源状态")
    public Result getStatus() {
        String username = UserContext.getUsername();
        return dataSourceService.getStatus(username);
    }

    @PostMapping("/switch")
    @LogExecutionTime("切换数据源")
    public Result switchDataSource(@RequestBody Map<String, Boolean> body) {
        String username = UserContext.getUsername();
        Boolean useCustom = body.get("useCustom");
        return dataSourceService.switchDataSource(username, useCustom);
    }
}