package org.mockserver.persistence;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.event.Level.INFO;

public class FileWatcher {

    private static ScheduledExecutorService scheduler;

    public synchronized static ScheduledExecutorService getScheduler() {
        if (scheduler == null) {
            scheduler = new ScheduledThreadPoolExecutor(
                2,
                new Scheduler.SchedulerThreadFactory("FileWatcher"),
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                scheduler.shutdown();
            }));
        }
        return scheduler;
    }

    private boolean running = true;
    private final ScheduledFuture<?> scheduledFuture;
    private static long pollPeriod = 5;
    private static TimeUnit pollPeriodUnits = TimeUnit.SECONDS;

    public FileWatcher(Path filePath, Runnable updatedHandler, Consumer<Throwable> errorHandler, MockServerLogger mockServerLogger) throws Exception {
        final Path path = filePath.getParent() != null ? filePath : Paths.get(new File(".").getAbsolutePath(), filePath.toString());
        final AtomicReference<Integer> fileHash = new AtomicReference<>(getFileHash(path));
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setMessageFormat("watching file:{}with file fingerprint:{}")
                .setArguments(path, fileHash)
        );
        scheduledFuture = getScheduler().scheduleAtFixedRate(() -> {
            try {
                if (!getFileHash(path).equals(fileHash.get())) {
                    updatedHandler.run();
                    fileHash.set(getFileHash(path));
                }
                MILLISECONDS.sleep(100);
            } catch (Throwable throwable) {
                if (!(throwable instanceof InterruptedException)) {
                    errorHandler.accept(throwable);
                }
            }
        }, pollPeriod, pollPeriod, pollPeriodUnits);
    }

    private Integer getFileHash(Path path) {
        try {
            return Arrays.hashCode(Files.readAllBytes(path));
        } catch (IOException ioe) {
            return 0;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public FileWatcher setRunning(boolean running) {
        this.running = running;
        if (!running && this.scheduledFuture != null) {
            this.scheduledFuture.cancel(true);
        }
        return this;
    }

    public static long getPollPeriod() {
        return FileWatcher.pollPeriod;
    }

    public static void setPollPeriod(long pollPeriod) {
        FileWatcher.pollPeriod = pollPeriod;
    }

    public static TimeUnit getPollPeriodUnits() {
        return FileWatcher.pollPeriodUnits;
    }

    public static void setPollPeriodUnits(TimeUnit pollPeriodUnits) {
        FileWatcher.pollPeriodUnits = pollPeriodUnits;
    }
}
