package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DynamicDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSource.class);

    private final UserDataSourceManager dataSourceManager;

    public DynamicDataSource(DataSource defaultDataSource, UserDataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        setDefaultTargetDataSource(defaultDataSource);
        Map<Object, Object> placeholder = new HashMap<>();
        placeholder.put("default", defaultDataSource);
        setTargetDataSources(placeholder);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return UserDataSourceContextHolder.get();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String username = UserDataSourceContextHolder.get();
        if (username == null || username.isEmpty()) {
            return dataSourceManager.getDefaultDataSource();
        }
        return dataSourceManager.getDataSource(username);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return determineTargetDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return determineTargetDataSource().getConnection(username, password);
    }
}