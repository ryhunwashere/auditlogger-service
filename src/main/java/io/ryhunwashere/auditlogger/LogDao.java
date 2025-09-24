package io.ryhunwashere.auditlogger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;

public class LogDao {
    public void initDatabase(String dbConfigPropertiesPath) throws SQLException {
    	try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        	System.err.println("PostgreSQL Driver not found!");
        }
    	
    	Properties props = new Properties();
    	try {
    		props.load(new FileInputStream(dbConfigPropertiesPath));
    	} catch (IOException e) {
    		e.printStackTrace();
    		System.err.println(dbConfigPropertiesPath + " is not found!");
    	}
    	
    	String schemaName = props.getProperty("schema.name");
    	String tableName = props.getProperty("table.name");
    	
    	if (schemaName == null || schemaName.isBlank()) 
    		throw new IllegalArgumentException(schemaName);
    	if (tableName == null || tableName.isBlank()) 
    		throw new IllegalArgumentException(tableName);
    	
    	try (Connection conn = DataSourceFactory.getDataSource().getConnection();
    			Statement stmt = conn.createStatement()) {
    		stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    		stmt.execute("CREATE TABLE IF NOT EXISTS " + schemaName + "." + tableName);
    	}
    }

    public int insertBatch(ArrayList<LogData> batch) throws SQLException {
        // TODO insert into main database
        return 0;
    }

    public int insertBatchToLocalDB(ArrayList<LogData> batch) throws SQLException {
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
