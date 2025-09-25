package io.ryhunwashere.auditlogger.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class SQLiteDataSourceFactory {
    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl("jdbc:sqlite:fallback.sqlite");
            config.setMaximumPoolSize(1);
            config.setDriverClassName("org.sqlite.JDBC");

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
}
