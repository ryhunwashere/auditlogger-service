package io.ryhunwashere.auditlogger.process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.ryhunwashere.auditlogger.PropsLoader;
import io.ryhunwashere.auditlogger.datasource.PGDataSourceFactory;
import io.ryhunwashere.auditlogger.datasource.SQLiteDataSourceFactory;
import io.ryhunwashere.auditlogger.process.LogDTO.ActionType;
import io.ryhunwashere.auditlogger.process.LogDTO.Source;
import io.ryhunwashere.auditlogger.util.DateTimeUtil;
import io.ryhunwashere.auditlogger.util.IdentifierValidator;

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

		if (dataSource instanceof PGDataSourceFactory) { // If dataSource is from Postgres
			sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + "id BIGSERIAL PRIMARY KEY, "
					+ "timestamp TIMESTAMPTZ NOT NULL, " + "player_uuid UUID NOT NULL, " + "player_name TEXT NOT NULL, "
					+ "action_type TEXT NOT NULL, " + "action_detail JSONB, " + "world TEXT NOT NULL, "
					+ "x DOUBLE PRECISION NOT NULL, " + "y DOUBLE PRECISION NOT NULL, "
					+ "z DOUBLE PRECISION NOT NULL, " + "action_name TEXT, " + "source TEXT)";
		} else if (dataSource instanceof SQLiteDataSourceFactory) { // If dataSource is from SQLite
			sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ "timestamp TEXT NOT NULL, " + "player_uuid TEXT NOT NULL, " + "player_name TEXT NOT NULL, "
					+ "action_type TEXT NOT NULL, " + "action_detail JSON, " + "world TEXT NOT NULL, "
					+ "x REAL NOT NULL, " + "y REAL NOT NULL, " + "z REAL NOT NULL, " + "action_name TEXT, "
					+ "source TEXT)";
		} else {
			throw new IllegalArgumentException(dataSource.getClass().getName());
		}

		try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
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
		Connection localConn = SQLiteDataSourceFactory.getDataSource().getConnection();
		Connection mainConn = PGDataSourceFactory.getDataSource().getConnection();

		ObjectMapper mapper = new ObjectMapper();
		TypeReference<Map<String, Object>> stringObjectMapRef = new TypeReference<>() {};

		try {
			localConn.setAutoCommit(false);
			mainConn.setAutoCommit(false);

			ArrayList<LogDTO> logDTOList = new ArrayList<>();
			
			// Add all non-transferred rows from SQLite into logDTOList
			try (PreparedStatement stmt = localConn.prepareStatement("SELECT * FROM fallback_logs WHERE transferred = 0")) {
				ResultSet localResultSet = stmt.executeQuery();
				while (localResultSet.next()) {
					Instant instantTimestamp = DateTimeUtil.stringToInstant(localResultSet.getString("timestamp"));
					UUID uuid = UUID.fromString(localResultSet.getString("uuid"));
					String name = localResultSet.getString("name");
					LogDTO.ActionType actionType = ActionType
							.valueOf(localResultSet.getString("action_type").toUpperCase());
					
					Map<String, Object> actionDetail;
					try {
						actionDetail = mapper.readValue(localResultSet.getString("action_detail"), stringObjectMapRef);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
						actionDetail = null;
					}
					
					String worldName = localResultSet.getString("world");
					double x = localResultSet.getDouble("x");
					double y = localResultSet.getDouble("y");
					double z = localResultSet.getDouble("z");
					LogDTO.Source source = Source.valueOf(localResultSet.getString("source").toUpperCase());
					
					LogDTO localDTO = new LogDTO(instantTimestamp, uuid, name, actionType, actionDetail, worldName, x, y, z, source);
					logDTOList.add(localDTO);
				}
			}
			
			// Insert into Postgres
			String insertSql = "INSERT INTO player_logs(timestamp, uuid, name, action_type, action_detail, world, x, y, z, source) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			try (PreparedStatement stmt = mainConn.prepareStatement(insertSql)) {
				for (LogDTO log : logDTOList) {
					stmt.setTimestamp(1, Timestamp.from(log.getInstantTimestamp()));
					stmt.setObject(2, log.getPlayerUUID());
					stmt.setString(3, log.getPlayerName());
					stmt.setString(4, log.getActionType().toString().toLowerCase());
					stmt.setObject(5, log.getActionDetail());
					stmt.setString(6, log.getWorld());
					stmt.setDouble(7, log.getX());
					stmt.setDouble(8, log.getY());
					stmt.setDouble(9, log.getZ());
					stmt.setString(10, log.getSource().toString().toLowerCase());
					stmt.addBatch();
				}
				stmt.executeBatch();
			}
			
			// Mark all transferred rows in SQLite
			try (Statement stmt = localConn.createStatement()) {
				stmt.executeUpdate("UPDATE fallback_logs SET transferred = 1 WHERE transferred = 0");
			}
			
			// Delete all transferred rows in SQLite
			try (Statement stmt = mainConn.createStatement()) {
				stmt.executeUpdate("DELETE * FROM fallback_logs WHERE transferred = 1");
			}

			mainConn.commit();
			localConn.commit();

		} catch (SQLException e) {
			localConn.rollback();
			mainConn.rollback();
			e.printStackTrace();

		} finally {
			localConn.setAutoCommit(true);
			localConn.close();
			mainConn.setAutoCommit(true);
			mainConn.close();
		}
		
		return 0;
	}
}
