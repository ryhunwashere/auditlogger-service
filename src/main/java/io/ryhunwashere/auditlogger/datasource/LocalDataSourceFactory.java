package io.ryhunwashere.auditlogger.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class LocalDataSourceFactory {
    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:fallback.db");
            config.setMaximumPoolSize(1);

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
}
