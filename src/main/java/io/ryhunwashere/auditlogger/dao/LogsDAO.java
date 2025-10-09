package io.ryhunwashere.auditlogger.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ryhunwashere.auditlogger.datasource.PGDataSourceFactory;
import io.ryhunwashere.auditlogger.datasource.SQLiteDataSourceFactory;
import io.ryhunwashere.auditlogger.dto.LogDTO;
import io.ryhunwashere.auditlogger.dto.LogDTO.ActionType;
import io.ryhunwashere.auditlogger.dto.LogDTO.Source;
import io.ryhunwashere.auditlogger.util.DateTimeUtil;
import io.ryhunwashere.auditlogger.util.IdentifierValidator;
import io.ryhunwashere.auditlogger.util.PropsLoader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class LogsDAO {
    private static final Logger log = LoggerFactory.getLogger(LogsDAO.class);
    private String postgresTableName;
    private String sqliteTableName;
    private final ObjectMapper mapper;
    private final DataSource postgresDataSource;
    private final DataSource sqliteDataSource;

    private final static int MAX_PLAYER_NAME_LENGTH = 15;
    private final static int CLEANUP_INTERVAL_DAYS = 3;

    public LogsDAO(String postgresTableName, String sqliteTableName) {
        this.mapper = JsonMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new JavaTimeModule());
        
        setTableNames(postgresTableName, sqliteTableName);
        verifyDrivers();

        this.postgresDataSource = PGDataSourceFactory.getDataSource(PropsLoader.getConfig("auditconfig"));
        this.sqliteDataSource = SQLiteDataSourceFactory.getDataSource();
        initDatabaseConnections(this.postgresDataSource, this.sqliteDataSource);
    }

    private void setTableNames(String postgresTableName, String sqliteTableName) {
        boolean postgresTableNameIsValid = IdentifierValidator.isValidIdentifier(postgresTableName);
        boolean sqliteTableNameIsValid = IdentifierValidator.isValidIdentifier(sqliteTableName);

        if (postgresTableName.isBlank())
            throw new IllegalArgumentException("Postgres database table name can neither be empty nor only contain whitespaces.");
        if (!postgresTableNameIsValid)
            throw new IllegalArgumentException("'" + postgresTableName + "' is invalid PostgreSQL table name.");

        if (sqliteTableName.isBlank())
            throw new IllegalArgumentException("SQLite database table name can neither be empty nor only contain whitespaces.");
        if (!sqliteTableNameIsValid)
            throw new IllegalArgumentException("'" + sqliteTableName + "' is invalid SQLite table name.");

        this.postgresTableName = postgresTableName;
        this.sqliteTableName = sqliteTableName;
    }

    private void verifyDrivers() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL Driver not found!");
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite Driver not found!");
        }
    }

    public void createMonthlyPartition() throws SQLException {
        createPartitionTables(postgresDataSource);
        createPartitionIndexes(postgresDataSource);

    }

    private void initDatabaseConnections(DataSource postgresDataSource, DataSource sqliteDataSource) {
        String postgresJdbcUrl = ((com.zaxxer.hikari.HikariDataSource) postgresDataSource).getJdbcUrl();
        String sqliteJdbcUrl = ((com.zaxxer.hikari.HikariDataSource) sqliteDataSource).getJdbcUrl();

        if (!postgresJdbcUrl.startsWith("jdbc:postgresql:"))
            throw new IllegalArgumentException("The provided Postgres data source must be for PostgreSQL database.");
        if (!sqliteJdbcUrl.startsWith("jdbc:sqlite:"))
            throw new IllegalArgumentException("The provided SQLite data source must be for SQLite database.");
        
        try {
            createTable(postgresDataSource);
            createMonthlyPartition();
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new RuntimeException("An error occurred when connecting to PostgreSQL database. Check the logs for details.");
        }

        try {
            createTable(sqliteDataSource);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new RuntimeException("An error occurred when connecting to SQLite database. Check the logs for details.");
        }
    }

    @Contract(pure = true)
    private @NotNull String sqlInsertIntoPostgres() {
        return "INSERT INTO " + postgresTableName
                + "(ts, player_uuid, player_name, action_type, action_detail, world, x, y, z, source, log_uuid) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (log_uuid, ts) DO NOTHING";
    }

    private void createTable(DataSource dataSource) throws SQLException {
        String sqlCreateTable = getSqlCreateTable(dataSource);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement createTableStmt = conn.prepareStatement(sqlCreateTable)) {
            createTableStmt.execute();
        }
    }

    private void createPartitionTables(@NotNull DataSource dataSource) throws SQLException {
        String jdbcUrl = ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl();
        if (!jdbcUrl.startsWith("jdbc:postgresql:"))
            throw new IllegalArgumentException("Table partitions can only be created for Postgres table.");

        ZoneId timezone = ZoneId.of(PropsLoader.getConfig("auditconfig").getString("server.timezone", "UTC"));
        LocalDate today = LocalDate.now(timezone);
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDate firstDayOfNextMonth = today.plusMonths(1).withDayOfMonth(1);
        LocalDate firstDayOfNextTwoMonths = today.plusMonths(2).withDayOfMonth(1);

        final int currentMonthNum = today.getMonthValue();
        final int partitionYear = currentMonthNum == 12 ? today.getYear() + 1 : today.getYear();
        final int nextMonthNum = currentMonthNum == 12 ? 1 : currentMonthNum + 1;

        final String sqlCreatePartitionForCurrentMonth =
                "CREATE TABLE IF NOT EXISTS " + postgresTableName + "_" + partitionYear + "_" + currentMonthNum + " "
                        + "PARTITION OF " + postgresTableName + " "
                        + "FOR VALUES FROM ('" + firstDayOfMonth + "') TO ('" + firstDayOfNextMonth + "')";
        final String sqlCreatePartitionForNextMonth =
                "CREATE TABLE IF NOT EXISTS " + postgresTableName + "_" + partitionYear + "_" + nextMonthNum + " "
                        + "PARTITION OF " + postgresTableName + " "
                        + "FOR VALUES FROM ('" + firstDayOfNextMonth + "') TO ('" + firstDayOfNextTwoMonths + "')";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmtCurrentMonthPartition = conn.prepareStatement(sqlCreatePartitionForCurrentMonth);
             PreparedStatement stmtNextMonthPartition = conn.prepareStatement(sqlCreatePartitionForNextMonth)) {
            stmtCurrentMonthPartition.execute();
            stmtNextMonthPartition.execute();
        }
    }

    private @NotNull String getSqlCreateTable(DataSource dataSource) {
        String jdbcUrl = ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl();
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return "CREATE TABLE IF NOT EXISTS " + postgresTableName + " ("
                    + "id BIGINT GENERATED ALWAYS AS IDENTITY, "
                    + "ts TIMESTAMPTZ NOT NULL, "
                    + "player_uuid UUID NOT NULL, "
                    + "player_name VARCHAR(" + MAX_PLAYER_NAME_LENGTH + ") NOT NULL, "
                    + "action_type TEXT NOT NULL, "
                    + "action_detail JSONB NOT NULL, "
                    + "world TEXT NOT NULL, "
                    + "x DOUBLE PRECISION NOT NULL, "
                    + "y DOUBLE PRECISION NOT NULL, "
                    + "z DOUBLE PRECISION NOT NULL, "
                    + "source TEXT NOT NULL, "
                    + "log_uuid UUID NOT NULL, "
                    + "PRIMARY KEY (ts, id), "
                    + "UNIQUE (log_uuid, ts)"
                    + ") "
                    + "PARTITION BY RANGE (ts)";
        }
        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            return "CREATE TABLE IF NOT EXISTS " + sqliteTableName + " ("
                    + "id INTEGER PRIMARY KEY, "
                    + "ts TEXT NOT NULL, "
                    + "player_uuid TEXT NOT NULL, "
                    + "player_name TEXT CHECK(length(player_name) <= " + MAX_PLAYER_NAME_LENGTH + ") NOT NULL, "
                    + "action_type TEXT NOT NULL, "
                    + "action_detail TEXT NOT NULL, "
                    + "world TEXT NOT NULL, "
                    + "x REAL NOT NULL, "
                    + "y REAL NOT NULL, "
                    + "z REAL NOT NULL, "
                    + "source TEXT NOT NULL, "
                    + "log_uuid TEXT UNIQUE"
                    + ")";
        }
        throw new IllegalArgumentException("Unsupported DataSource type: " + dataSource.getClass().getName());
    }

    private void createPartitionIndexes(DataSource dataSource) throws SQLException {
        String jdbcUrl = ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl();
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) return;

        ZoneId timezone = ZoneId.of(PropsLoader.getConfig("auditconfig").getString("server.timezone", "UTC"));
        LocalDate today = LocalDate.now(timezone);

        int currentMonthNum = today.getMonthValue();
        int nextMonthNum = currentMonthNum == 12 ? 1 : currentMonthNum + 1;
        int currentYear = today.getYear();
        int nextYear = currentMonthNum == 12 ? currentYear + 1 : currentYear;

        String[] partitions = {
                postgresTableName + "_" + currentYear + "_" + currentMonthNum,
                postgresTableName + "_" + nextYear + "_" + nextMonthNum
        };

        try (Connection conn = dataSource.getConnection()) {
            for (String partition : partitions) {
                String playerUuidIdx = "CREATE INDEX IF NOT EXISTS idx_" + partition + "_player_uuid " +
                        "ON " + partition + "(player_uuid)";
                try (PreparedStatement stmt = conn.prepareStatement(playerUuidIdx)) {
                    stmt.execute();
                }

                String locationIdx = "CREATE INDEX IF NOT EXISTS idx_" + partition + "_world_xyz " +
                        "ON " + partition + "(world, x, y, z)";
                try (PreparedStatement stmt = conn.prepareStatement(locationIdx)) {
                    stmt.execute();
                }

                String actionTypeIdx = "CREATE INDEX IF NOT EXISTS idx_" + partition + "_action_type " +
                        "ON " + partition + "(action_type)";
                try (PreparedStatement stmt = conn.prepareStatement(actionTypeIdx)) {
                    stmt.execute();
                }

                String actionDetailGinIdx = "CREATE INDEX IF NOT EXISTS idx_" + partition + "_action_detail_gin " +
                        "ON " + partition + " USING gin (action_detail)";
                try (PreparedStatement stmt = conn.prepareStatement(actionDetailGinIdx)) {
                    stmt.execute();
                }
            }
        }
    }

    public int insertToPostgres(@NotNull List<LogDTO> batch) throws SQLException {
        int[] insertStatements = null;
        try (Connection conn = postgresDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sqlInsertIntoPostgres())) {
                insertStatements = insertBatchToPostgres(stmt, batch);
                conn.commit();
            } catch (SQLException | JsonProcessingException e) {
                conn.rollback();
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
        return insertStatements != null ? insertStatements.length : 0;
    }

    private int[] insertBatchToPostgres(PreparedStatement stmt, @NotNull List<LogDTO> logDTOList)
            throws SQLException, JsonProcessingException {
        for (LogDTO log : logDTOList) {
            stmt.setTimestamp(1, Timestamp.from(log.getTimestamp()));
            stmt.setObject(2, log.getPlayerUUID());
            stmt.setString(3, log.getPlayerName());
            stmt.setString(4, log.getActionType().toString().toLowerCase());
            String json = mapper.writeValueAsString(log.getActionDetail());
            PGobject jsonObj = new PGobject();
            jsonObj.setType("jsonb");
            jsonObj.setValue(json);
            stmt.setObject(5, jsonObj);
            stmt.setString(6, log.getWorld());
            stmt.setDouble(7, log.getX());
            stmt.setDouble(8, log.getY());
            stmt.setDouble(9, log.getZ());
            stmt.setString(10, log.getSource().toString().toLowerCase());
            stmt.setObject(11, log.getLogUUID());
            stmt.addBatch();
        }
        return stmt.executeBatch();
    }

    public int insertToSQLite(@NotNull List<LogDTO> batch) throws SQLException {
        String sql = "INSERT INTO " + sqliteTableName + " "
                + "(ts, player_uuid, player_name, action_type, action_detail, world, x, y, z, source, log_uuid) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int[] insertedRows;
        try (Connection conn = SQLiteDataSourceFactory.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                insertedRows = insertBatchToSQLite(stmt, batch);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                insertedRows = null;
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }

        return insertedRows != null ? insertedRows.length : 0;
    }

    private int[] insertBatchToSQLite(PreparedStatement stmt, @NotNull List<LogDTO> logDTOList) throws SQLException {
        for (LogDTO log : logDTOList) {
            stmt.setString(1, Timestamp.from(log.getTimestamp()).toString());
            stmt.setString(2, log.getPlayerUUID().toString());
            stmt.setString(3, log.getPlayerName());
            stmt.setString(4, log.getActionType().toString().toLowerCase());
            try {
                String actionDetailJson = mapper.writeValueAsString(log.getActionDetail());
                stmt.setString(5, actionDetailJson);
            } catch (JsonProcessingException e) {
                System.err.println(e.getMessage());
            }
            stmt.setString(6, log.getWorld());
            stmt.setDouble(7, log.getX());
            stmt.setDouble(8, log.getY());
            stmt.setDouble(9, log.getZ());
            stmt.setString(10, log.getSource().toString().toLowerCase());
            stmt.setString(11, log.getLogUUID().toString());
            stmt.addBatch();
        }
        return stmt.executeBatch();
    }

    public int getLocalDBLogsCount() throws SQLException {
        int localRowsCount = 0;
        final String sql = "SELECT COUNT(*) AS total FROM " + sqliteTableName;
        try (Connection conn = SQLiteDataSourceFactory.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next())
                localRowsCount = rs.getInt("total");
        }
        return localRowsCount;
    }

    public void flushLocalToMainDB() throws SQLException {
        try (Connection sqliteConn = SQLiteDataSourceFactory.getDataSource().getConnection();
             Connection postgresConn = postgresDataSource.getConnection()) {
            sqliteConn.setAutoCommit(false);
            postgresConn.setAutoCommit(false);
            try {
                // Cleanup transferred rows in SQLite if there's any
                List<UUID> logUUIDList = new ArrayList<>();
                final String sqlSelectLastThreeDays = "SELECT log_uuid FROM " + postgresTableName + " "
                        + "WHERE ts >= NOW() - INTERVAL '" + CLEANUP_INTERVAL_DAYS + " days'";
                try (PreparedStatement stmt = postgresConn.prepareStatement(sqlSelectLastThreeDays);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID logUUID = rs.getObject("log_uuid", UUID.class);
                        logUUIDList.add(logUUID);
                    }
                }

                final String sqlDeleteTransferredLogs = "DELETE FROM " + sqliteTableName + " WHERE log_uuid = ?";
                try (PreparedStatement stmt = sqliteConn.prepareStatement(sqlDeleteTransferredLogs)) {
                    for (UUID logUUID : logUUIDList) {
                        stmt.setString(1, logUUID.toString());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                sqliteConn.commit();

                List<LogDTO> logDTOList = new ArrayList<>();
                final String sqlSelectAllLocalRows = "SELECT ts, player_uuid, player_name, action_type, action_detail, "
                        + "world, x, y, z, source, log_uuid FROM " + sqliteTableName;
                try (PreparedStatement stmt = sqliteConn.prepareStatement(sqlSelectAllLocalRows);
                     ResultSet localResultSet = stmt.executeQuery()) {
                    while (localResultSet.next()) {
                        Instant ts = DateTimeUtil.stringToInstant(
                                localResultSet.getString("ts"));
                        UUID playerUUID = UUID.fromString(localResultSet.getString("player_uuid"));
                        String playerName = localResultSet.getString("player_name");
                        ActionType actionType = ActionType.valueOf(
                                localResultSet.getString("action_type").toUpperCase());

                        // Potentially throws JsonParseException
                        String json = localResultSet.getString("action_detail");
                        Map<String, Object> actionDetail = mapper.readValue(json, new TypeReference<>() {
                        });

                        String world = localResultSet.getString("world");
                        double x = localResultSet.getDouble("x");
                        double y = localResultSet.getDouble("y");
                        double z = localResultSet.getDouble("z");
                        Source source = Source.valueOf(localResultSet.getString("source").toUpperCase());
                        UUID logUUID = UUID.fromString(localResultSet.getString("log_uuid"));

                        LogDTO log = new LogDTO();
                        log.setTimestamp(ts);
                        log.setPlayerUUID(playerUUID);
                        log.setPlayerName(playerName);
                        log.setActionType(actionType);
                        log.setActionDetail(actionDetail);
                        log.setWorld(world);
                        log.setX(x);
                        log.setY(y);
                        log.setZ(z);
                        log.setSource(source);
                        log.setLogUUID(logUUID);

                        logDTOList.add(log);
                    }
                } catch (SQLException | JsonProcessingException e) {
                    e.printStackTrace();
                }

                // Insert from logDTOList into Postgres
                try (PreparedStatement stmt = postgresConn.prepareStatement(sqlInsertIntoPostgres())) {
                    insertBatchToPostgres(stmt, logDTOList);
                } catch (SQLException | JsonProcessingException e) {
                    e.printStackTrace();
                }
                postgresConn.commit();

            } catch (SQLException e) {
                sqliteConn.rollback();
                postgresConn.rollback();
                e.printStackTrace();
            }
        }
    }

    public List<LogDTO> getLogsOnCurrentLoc(String world, double radius, double x, double z,
                                            Instant since, Instant until, int limit) throws SQLException {
        String sql = "SELECT ts, player_name, action_type, action_detail, world, x, y, z " +
                "FROM " + postgresTableName + " " +
                "WHERE world = ? " +
                "AND x BETWEEN (? - ?) AND (? + ?) " +
                "AND z BETWEEN (? - ?) AND (? + ?) " +
                "AND ts BETWEEN ? AND ? " +
                "ORDER BY ts DESC, id ASC " +
                "LIMIT ?";
        try (Connection conn = postgresDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, radius);
            stmt.setDouble(4, x);
            stmt.setDouble(5, radius);
            stmt.setDouble(6, z);
            stmt.setDouble(7, radius);
            stmt.setDouble(8, z);
            stmt.setDouble(9, radius);
            stmt.setTimestamp(10, Timestamp.from(since));
            stmt.setTimestamp(11, Timestamp.from(until));
            stmt.setInt(12, limit);

            try {
                return getResultAsList(stmt);
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public List<LogDTO> getLogsOfPlayer(UUID playerUuid, Instant since, Instant until, int limit) throws SQLException {
        String sql = "SELECT ts, player_name, action_type, action_detail, world, x, y, z " +
                "FROM " + postgresTableName + " " +
                "WHERE player_uuid = ? " +
                "AND ts BETWEEN ? AND ? " +
                "ORDER BY ts DESC, id ASC " +
                "LIMIT ?";
        try (Connection conn = PGDataSourceFactory.getDataSource(PropsLoader.getConfig("auditconfig")).getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setTimestamp(2, Timestamp.from(since));
            stmt.setTimestamp(3, Timestamp.from(until));
            stmt.setInt(4, limit);

            try {
                return getResultAsList(stmt);
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private List<LogDTO> getResultAsList(PreparedStatement stmt) throws SQLException {
        List<LogDTO> logDTOList = null;
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                logDTOList = new LinkedList<>();
                do {
                    LogDTO log = new LogDTO();
                    log.setTimestamp(rs.getTimestamp("ts").toInstant());
                    log.setPlayerName(rs.getString("player_name"));
                    log.setActionType(rs.getString("action_type"));
                    try {
                        String actionDetailString = rs.getString("action_detail");
                        Map<String, Object> actionDetailMap = mapper.readValue(actionDetailString, new TypeReference<>() {
                        });
                        log.setActionDetail(actionDetailMap);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    log.setWorld(rs.getString("world"));
                    log.setX(rs.getDouble("x"));
                    log.setY(rs.getDouble("y"));
                    log.setZ(rs.getDouble("z"));
                    logDTOList.add(log);
                } while (rs.next());
            }
        }
        return logDTOList;
    }
}
