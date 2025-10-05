package io.ryhunwashere.auditlogger.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ryhunwashere.auditlogger.util.Config;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PGDataSourceFactory {
    private static final Map<Config, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    public static DataSource getDataSource(Config config) {
        return dataSources.computeIfAbsent(config, cfg -> {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(cfg.getString("dataSource.url"));
            hikariConfig.setUsername(cfg.getString("dataSource.user"));
            hikariConfig.setPassword(cfg.getString("dataSource.password"));
            hikariConfig.setMaximumPoolSize(cfg.getInt("dataSource.maximumPoolSize"));
            hikariConfig.setMinimumIdle(cfg.getInt("dataSource.minimumIdle"));
            hikariConfig.setIdleTimeout(cfg.getLong("dataSource.idleTimeoutSeconds") * 1000);
            hikariConfig.setConnectionTimeout(cfg.getLong("dataSource.connectionTimeoutSeconds") * 1000);
            hikariConfig.setDriverClassName("org.postgresql.Driver");

            return new HikariDataSource(hikariConfig);
        });
    }
}
