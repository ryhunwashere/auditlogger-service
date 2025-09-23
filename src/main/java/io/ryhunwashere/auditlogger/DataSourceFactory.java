package io.ryhunwashere.auditlogger;

import com.zaxxer.hikari.HikariDataSource;

import com.zaxxer.hikari.HikariConfig;

import javax.sql.DataSource;

public class DataSourceFactory {
    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydatabase");
            config.setUsername("myuser");
            config.setPassword("mypassword");
            
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30 * 1000);
            config.setConnectionTimeout(20 * 1000);
            config.setLeakDetectionThreshold(15 * 1000);

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
}
