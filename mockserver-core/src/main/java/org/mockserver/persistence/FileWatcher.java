package org.mockserver.persistence;

import org.mockserver.scheduler.Scheduler;

import java.nio.file.*;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class FileWatcher {

    private boolean running = true;

    public FileWatcher(String filePath, Runnable updatedHandler, Consumer<Throwable> errorHandler) throws Exception {
        WatchService watchService;
        Path directoryPath = Paths.get(filePath);
        Path fileName = directoryPath.getFileName();
        watchService = FileSystems.getDefault().newWatchService();
        directoryPath.getParent()
            .register(
                watchService,
                StandardWatchEventKinds.OVERFLOW,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY
            );

        new Scheduler.SchedulerThreadFactory(FileWatcher.class.getSimpleName()).newThread(() -> {
            while (isRunning()) {
                try {
                    WatchKey key = watchService.take();

                    if (isRunning()) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.context() instanceof Path && ((Path) event.context()).getFileName().equals(fileName)) {
                                // ensure file has been committed to file system
                                MILLISECONDS.sleep(100);
                                updatedHandler.run();
                                break;
                            }
                        }

                        key.reset();
                    }
                } catch (Throwable throwable) {
                    errorHandler.accept(throwable);
                }
            }
        }).start();
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
