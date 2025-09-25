package io.ryhunwashere.auditlogger.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ryhunwashere.auditlogger.PropsLoader;

import javax.sql.DataSource;

public class PGDataSourceFactory {
    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl(PropsLoader.getString("dataSource.url"));
            config.setUsername(PropsLoader.getString("dataSource.user"));
            config.setPassword(PropsLoader.getString("dataSource.password"));
            config.setMaximumPoolSize(PropsLoader.getInt("dataSource.maximumPoolSize"));
            config.setMinimumIdle(PropsLoader.getInt("dataSource.minimumIdle"));
            config.setIdleTimeout(PropsLoader.getLong("dataSource.idleTimeoutSeconds") * 1000);
            config.setConnectionTimeout(PropsLoader.getLong("dataSource.connectionTimeoutSeconds") * 1000);
            config.setDriverClassName("org.postgresql.Driver");

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
}
