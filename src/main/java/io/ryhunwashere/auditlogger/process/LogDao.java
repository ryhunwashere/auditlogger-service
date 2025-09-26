package io.ryhunwashere.auditlogger.process;

import io.ryhunwashere.auditlogger.PropsLoader;
import io.ryhunwashere.auditlogger.datasource.PGDataSourceFactory;
import io.ryhunwashere.auditlogger.datasource.SQLiteDataSourceFactory;
import io.ryhunwashere.auditlogger.util.IdentifierValidator;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class LogDAO {
    public void initDatabase() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("PostgreSQL Driver not found!");
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("SQLite Driver not found!");
        }

        String tableName = PropsLoader.getString("db.tableName");

        // Initialize main database
        createTable(PGDataSourceFactory.getDataSource(), tableName);

        // Initialize local fallback database
        createTable(SQLiteDataSourceFactory.getDataSource(), tableName);
    }

    private void createTable(DataSource dataSource, @NotNull String tableName) {
        boolean tableNameIsValid = IdentifierValidator.isValidIdentifier(tableName);

        if (tableName.isBlank() || !tableNameIsValid)
            throw new IllegalArgumentException(tableName);

        String sql;

        if (dataSource instanceof PGDataSourceFactory) {                    // If dataSource is from Postgres
            sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "timestamp TIMESTAMPTZ NOT NULL, "
                    + "player_uuid UUID NOT NULL, "
                    + "player_name TEXT NOT NULL, "
                    + "action_type TEXT NOT NULL, "
                    + "action_detail JSONB, "
                    + "world TEXT NOT NULL, "
                    + "x DOUBLE PRECISION NOT NULL, "
                    + "y DOUBLE PRECISION NOT NULL, "
                    + "z DOUBLE PRECISION NOT NULL, "
                    + "action_name TEXT, "
                    + "source TEXT)";
        } else if (dataSource instanceof SQLiteDataSourceFactory) {            // If dataSource is from SQLite
            sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "timestamp TEXT NOT NULL, "
                    + "player_uuid TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, "
                    + "action_type TEXT NOT NULL, "
                    + "action_detail JSON, "
                    + "world TEXT NOT NULL, "
                    + "x REAL NOT NULL, "
                    + "y REAL NOT NULL, "
                    + "z REAL NOT NULL, "
                    + "action_name TEXT, "
                    + "source TEXT)";
        } else {
            throw new IllegalArgumentException(dataSource.getClass().getName());
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Table creation failed for " + dataSource.getClass().getName());
            e.printStackTrace();
        }
    }

    public int insertBatch(ArrayList<LogDTO> batch) throws SQLException {
        // TODO insert into main database
        try (Connection conn = PGDataSourceFactory.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int insertBatchToLocalDB(ArrayList<LogDTO> batch) throws SQLException {
        // TODO insert into local DB (SQLite)
        return 0;
    }

    public int getLocalDBLogsCount() throws SQLException {
        // TODO count the rows from SQLite
        return 0;
    }

    public int flushLocalToMainDB() throws SQLException {
        // TODO transfer process from SQLite DB into main DB
        return 0;
    }
}
