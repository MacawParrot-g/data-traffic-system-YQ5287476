package org.example.service;

import org.example.common.Result;

public interface DataSourceService {
    Result getConfig(String username);
    Result saveConfig(String username, String url, String dsUsername, String password);
    Result deleteConfig(String username);
    Result testConnection(String url, String dsUsername, String password);
    Result getStatus(String username);
    Result switchDataSource(String username, Boolean useCustom);
}
