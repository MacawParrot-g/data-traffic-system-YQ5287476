package org.example.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DynamicDataSourceConfig {

    @Bean
    @Primary
    public DynamicDataSource dynamicDataSource(DataSourceProperties properties,
                                               UserDataSourceManager userDataSourceManager) {
        HikariDataSource defaultDs = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        return new DynamicDataSource(defaultDs, userDataSourceManager);
    }
}
