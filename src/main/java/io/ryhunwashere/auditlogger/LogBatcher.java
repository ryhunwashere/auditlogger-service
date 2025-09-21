package io.ryhunwashere.auditlogger;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LogBatcher {
    private final BlockingQueue<LogData> queue = new LinkedBlockingQueue<>();
    private final LogDao dao;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService vt;

    private AtomicInteger fallbackLogsCount;  // How many logs left in the local (SQLite) database

    private static final long SHUTDOWN_TIMEOUT = 30L;
    private static final long FLUSH_INTERVAL = 5L;
    private static final long LOCAL_FLUSH_INTERVAL = 30L;

    public LogBatcher(LogDao dao, ExecutorService vt) {
        this.vt = vt;
        this.dao = dao;
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Schedule flushing logs to main DB
        scheduler.scheduleAtFixedRate(this::flushLogs, FLUSH_INTERVAL, FLUSH_INTERVAL, TimeUnit.SECONDS);

        // Schedule flushing logs from fallback DB if there's any
        scheduler.scheduleAtFixedRate(this::flushLocalLogs, LOCAL_FLUSH_INTERVAL, LOCAL_FLUSH_INTERVAL, TimeUnit.SECONDS);
    }

    public void shutdownBatcher() {
        if (!scheduler.isShutdown()) {
            System.out.println("Attempting to shutdown batcher..");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                    System.out.println("Forcing to shutdown batcher..");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (scheduler.isShutdown())
            System.out.println("Shutdown successful!");
    }

    public void addLog(LogData log) {
        queue.add(log);
    }

    public void addLogs(List<LogData> logs) {
        queue.addAll(logs);
    }

    /**
     * Get count of fallback logs in the local SQLite database and set it into fallbackLogsCount. <br>
     * Ran once on server startup.
     */
    public void initFallbackLogsCount() {
        try {
            fallbackLogsCount = new AtomicInteger(dao.getLocalDBLogsCount());
        } catch (SQLException e) {
            e.printStackTrace();
            fallbackLogsCount = new AtomicInteger();  // safe default
        }
    }

    private void flushLogs() {
        vt.submit(() -> {
            ArrayList<LogData> batch = new ArrayList<>(100);
            try {
                // Block until at least 1 row arrives
                LogData firstLog = queue.take();
                batch.add(firstLog);

                queue.drainTo(batch, 99);

                System.out.println("Flushing " + batch.size() + " logs...");
                dao.insertBatch(batch);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();

            } catch (SQLException e) {
                System.err.println("Flush to main DB failed!");
                insertIntoLocal(batch);
                e.printStackTrace();
            }
        });
    }

    private void insertIntoLocal(@NotNull ArrayList<LogData> batch) {
        vt.submit(() -> {
            try {
                dao.insertBatchToLocalDB(batch);
                fallbackLogsCount.addAndGet(batch.size());

                System.out.println("Inserting " + batch.size() + " logs into local DB." +
                        "\n Total fallback: " + fallbackLogsCount.get() + " logs.");

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void flushLocalLogs() {
        if (fallbackLogsCount.get() > 0) {
            vt.submit(() -> {
                try {
                    int flushedLogs = dao.flushLocalToMainDB();
                    fallbackLogsCount.getAndAdd(-flushedLogs);
                    if (fallbackLogsCount.get() < 0) fallbackLogsCount.set(0);

                    System.out.println("Flushed " + flushedLogs + " logs from local DB.");

                } catch (SQLException e) {
                    System.err.println("Failed to flush local logs.");
                    e.printStackTrace();
                }
            });
        }
    }
}
