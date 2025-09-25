package io.ryhunwashere.auditlogger.process;

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
    private final int batchSize;
    private AtomicInteger fallbackLogsCount;  // How many logs left in the local fallback database

    private static final int MIN_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 500;
    private static final long SHUTDOWN_TIMEOUT = 30L;
    private static final long FLUSH_INTERVAL = 5L;
    private static final long LOCAL_FLUSH_INTERVAL = 30L;

    public LogBatcher(LogDao dao, ExecutorService virtualThread, int batchSize) {
        this.dao = dao;
        this.vt = virtualThread;

        if (batchSize < MIN_BATCH_SIZE) {
            System.out.println("Batch size setting (" + batchSize + ") is too low!" +
                    "/n Batch size is set to minimum size: " + MIN_BATCH_SIZE);
            this.batchSize = MIN_BATCH_SIZE;

        } else if (batchSize > MAX_BATCH_SIZE) {
            System.out.println("Batch size setting (" + batchSize + ") is too high!" +
                    "/n Batch size is set to maximum size: " + MAX_BATCH_SIZE);
            this.batchSize = MAX_BATCH_SIZE;

        } else {
            this.batchSize = batchSize;
            System.out.println("Batch size set to: " + this.batchSize);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Schedule flushing logs to main DB
        scheduler.scheduleAtFixedRate(this::flushLogs, FLUSH_INTERVAL, FLUSH_INTERVAL, TimeUnit.SECONDS);

        // Schedule flushing logs from fallback DB if there's any
        scheduler.scheduleAtFixedRate(this::flushLocalLogs, LOCAL_FLUSH_INTERVAL, LOCAL_FLUSH_INTERVAL, TimeUnit.SECONDS);

        // Get and set count of fallback logs in the local SQLite database
        try {
            fallbackLogsCount = new AtomicInteger(dao.getLocalDBLogsCount());
            System.out.println("Fallback logs in local SQLite DB: " + fallbackLogsCount.get() + " rows");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Cannot fetch current fallback rows in local SQLite DB.");
            fallbackLogsCount = new AtomicInteger();  // safe default
        }
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

    private void flushLogs() {
        vt.submit(() -> {
            ArrayList<LogData> batch = new ArrayList<>(batchSize);
            try {
                // Block until at least 1 row arrives
                LogData firstLog = queue.take();
                batch.add(firstLog);

                // Instantly grab whatever else is available
                queue.drainTo(batch, batchSize - 1);

                // Try to grab more if batch is underfilled
                while (batch.size() < batchSize) {
                    LogData next = queue.poll(3, TimeUnit.SECONDS);
                    if (next == null) break; // Timeout reached
                    batch.add(next);
                }

                System.out.println("Flushing " + batch.size() + " logs...");
                dao.insertBatch(batch);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();

            } catch (SQLException e) {
                System.err.println("Flush to main DB failed!");
                e.printStackTrace();
                insertIntoLocal(batch);
            }
        });
    }

    private void insertIntoLocal(@NotNull ArrayList<LogData> batch) {
        vt.submit(() -> {
            try {
                int fallbackLogs = dao.insertBatchToLocalDB(batch);
                fallbackLogsCount.addAndGet(batch.size());

                System.out.println("Inserting " + batch.size() + " logs into local DB." +
                        "\n Total fallback: " + fallbackLogs + " logs.");

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
                    e.printStackTrace();
                    System.err.println("Failed to flush local logs into main DB.");
                }
            });
        }
    }
}
