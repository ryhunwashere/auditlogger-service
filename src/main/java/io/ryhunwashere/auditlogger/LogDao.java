package io.ryhunwashere.auditlogger;

import java.sql.SQLException;
import java.util.ArrayList;

public class LogDao {
    public void initDatabaseConnection() throws SQLException {
        // TODO connect into main database (Postgres)
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
