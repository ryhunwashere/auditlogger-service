package io.ryhunwashere.auditlogger.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class DataSourceFactory {
    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig("config.properties");
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
}
