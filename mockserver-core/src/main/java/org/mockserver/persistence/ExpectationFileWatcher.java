package org.mockserver.persistence;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.ui.MockServerMatcherNotifier;
import org.slf4j.event.Level;

import java.nio.file.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

public class ExpectationFileWatcher {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final MockServerMatcher mockServerMatcher;

    public ExpectationFileWatcher(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        if (ConfigurationProperties.watchInitializationJson()) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
            this.mockServerLogger = mockServerLogger;
            this.mockServerMatcher = mockServerMatcher;

            WatchService watchService;
            Path filePath = Paths.get(ConfigurationProperties.initializationClass());
            try {
                watchService = FileSystems.getDefault().newWatchService();
                filePath
                    .register(
                        watchService,
                        StandardWatchEventKinds.OVERFLOW,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                    );
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Exception file watcher for " + filePath.toString())
                        .setThrowable(throwable)
                );
                return;
            }

            new Scheduler.SchedulerThreadFactory(ExpectationFileWatcher.class.getSimpleName()).newThread(() -> {
                while (true) {
                    WatchKey key;
                    try {
                        // return signaled key, meaning events occurred on the object
                        key = watchService.take();
                    } catch (InterruptedException ex) {
                        return;
                    }

                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(LogEntry.LogMessageType.DEBUG)
                                .setLogLevel(DEBUG)
                                .setMessageFormat("Events occurred on expectation file being watched " + key.pollEvents().stream().map(event -> event.kind().name()).collect(Collectors.toList()))
                        );
                    }

                    try {
                        addExpectationsFromInitializer();
                    } catch (Throwable throwable) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(LogEntry.LogMessageType.WARN)
                                .setLogLevel(WARN)
                                .setMessageFormat("Exception while processing expectation file update " + throwable.getMessage())
                                .setThrowable(throwable)
                        );
                    }

                    // resetting the key goes back ready state
                    key.reset();
                }
            }).start();
        } else {
            this.expectationSerializer = null;
            this.mockServerLogger = null;
            this.mockServerMatcher = null;
        }
    }

    private void addExpectationsFromInitializer() {
        mockServerMatcher.update(retrieveExpectationsFromJson(), MockServerMatcherNotifier.Cause.FILE_WATCHER);
    }

    private Expectation[] retrieveExpectationsFromJson() {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        if (isNotBlank(initializationJsonPath)) {
            try {
                return expectationSerializer.deserializeArray(FileReader.readFileFromClassPathOrPath(initializationJsonPath));
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(WARN)
                        .setMessageFormat("Exception while loading JSON initialization file, ignoring file")
                        .setThrowable(throwable)
                );
            }
        }
        return new Expectation[0];
    }

}
