package io.ryhunwashere.auditlogger.process;

import io.ryhunwashere.auditlogger.dao.LogsDAO;
import io.ryhunwashere.auditlogger.dto.LogDTO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LogsManager {
    private static final Logger log = LoggerFactory.getLogger(LogsManager.class);
    private final BlockingQueue<LogDTO> queue = new LinkedBlockingQueue<>();

    private final LogsDAO dao;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService vt;
    private final int batchSize;
    private AtomicInteger fallbackLogsCount;  // How many logs left in the local fallback database

    private static final int MIN_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 500;
    private static final long SHUTDOWN_TIMEOUT = 30L;
    private static final long FLUSH_INTERVAL = 2L;
    private static final long LOCAL_FLUSH_INTERVAL = 10L;

    public LogsManager(LogsDAO dao, ExecutorService virtualThread, int batchSize) {
        this.dao = dao;
        this.vt = virtualThread;

        if (batchSize < MIN_BATCH_SIZE) {
            System.out.println("Batch size setting (" + batchSize + ") is too low!");
            System.out.println("Batch size is set to minimum size: " + MIN_BATCH_SIZE);
            this.batchSize = MIN_BATCH_SIZE;
        } else if (batchSize > MAX_BATCH_SIZE) {
            System.out.println("Batch size setting (" + batchSize + ") is too high!");
            System.out.println("Batch size is set to maximum size: " + MAX_BATCH_SIZE);
            this.batchSize = MAX_BATCH_SIZE;
        } else {
            this.batchSize = batchSize;
            System.out.println("Batch size set to: " + this.batchSize);
        }

        // Schedulers for flushing to main DB & flushing from local DB to main DB
        scheduler = Executors.newScheduledThreadPool(4);
        scheduler.scheduleWithFixedDelay(this::flushLogs, 5, FLUSH_INTERVAL, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::flushLocalLogs, 5, LOCAL_FLUSH_INTERVAL, TimeUnit.SECONDS);

        // Create new table partition every start of month
        scheduleMonthlyPartition();

        // Get and set count of fallback logs in the local SQLite database
        try {
            fallbackLogsCount = new AtomicInteger(dao.getLocalDBLogsCount());
            System.out.println("Fallback logs in local SQLite DB: " + fallbackLogsCount.intValue() + " rows");
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

    public void addLog(LogDTO log) {
        queue.add(log);
    }

    public void addLogs(List<LogDTO> logs) {
        queue.addAll(logs);
    }

    private void scheduleMonthlyPartition() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextFirst = now.withDayOfMonth(1).plusMonths(1)
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        long delay = Duration.between(now, nextFirst).toMillis();
        scheduler.schedule(() -> {
            try {
                dao.createMonthlyPartition();
            } catch (SQLException e) {
                System.err.println(e.getMessage());
                log.error(e.getMessage());
            } finally {
                scheduleMonthlyPartition();
            }
        }, delay, TimeUnit.SECONDS);

        System.out.println("Next monthly partition creation scheduled for " + nextFirst);
    }

    private void flushLogs() {
//        vt.submit(() -> {
        List<LogDTO> batch = new ArrayList<>();
        try {
            // Block until at least 1 row arrives
            LogDTO firstLog = queue.take();
            batch.add(firstLog);

            // Instantly grab whatever else is available
            queue.drainTo(batch, batchSize - 1);

            // Try to grab more if batch is underfilled
            while (batch.size() < batchSize) {
                LogDTO next = queue.poll(3, TimeUnit.SECONDS);
                if (next == null) break; // Timeout reached
                batch.add(next);
            }

            int flushedLogs = dao.insertToPostgres(batch);
            System.out.println("Successfully flushed " + flushedLogs + " logs into main DB!");
            System.out.println("Current logs in queue: " + queue.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Flush to main DB failed! Attempting to insert into local fallback DB..");
            insertIntoLocal(batch);
        }
//        });
    }

    private void insertIntoLocal(@NotNull List<LogDTO> batch) {
        try {
            int insertedFallbackLogs = dao.insertToSQLite(batch);
            System.out.println("Inserted " + insertedFallbackLogs + " logs into local DB.");
            fallbackLogsCount.addAndGet(insertedFallbackLogs);
            System.out.println("Total fallback: " + fallbackLogsCount.intValue() + " logs.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void flushLocalLogs() {
        if (fallbackLogsCount.intValue() <= 0) {
            fallbackLogsCount.set(0);   // added safety in case the count goes negative
            return;
        }

        try {
            dao.flushLocalToMainDB();
            int currentFallbackLogs = dao.getLocalDBLogsCount();
            fallbackLogsCount.set(currentFallbackLogs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<LogDTO> getLogsOnCurrentLoc(String world, double radius, double x, double z,
                                            Instant since, Instant until, int limit) {
        try {
            return dao.getLogsOnCurrentLoc(world, radius, x, z, since, until, limit);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<LogDTO> getLogsOfPlayer(UUID playerUuid, Instant since, Instant until, int limit) {
        try {
            return dao.getLogsOfPlayer(playerUuid, since, until, limit);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
