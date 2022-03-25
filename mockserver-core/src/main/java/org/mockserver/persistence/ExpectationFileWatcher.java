package org.mockserver.persistence;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.slf4j.event.Level;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class ExpectationFileWatcher {

    private final Configuration configuration;
    private final ExpectationInitializerLoader expectationInitializerLoader;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;
    private final ExpectationSerializer expectationSerializer;
    private List<FileWatcher> fileWatchers;

    public ExpectationFileWatcher(Configuration configuration, MockServerLogger mockServerLogger, RequestMatchers requestMatchers, ExpectationInitializerLoader expectationInitializerLoader) {
        this.configuration = configuration;
        if (configuration.watchInitializationJson()) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
            this.mockServerLogger = mockServerLogger;
            this.requestMatchers = requestMatchers;
            this.expectationInitializerLoader = expectationInitializerLoader;
            List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths(configuration.initializationJsonPath());
            try {
                fileWatchers = initializationJsonPaths
                    .stream()
                    .map(initializationJsonPath -> {
                        try {
                            return new FileWatcher(Paths.get(initializationJsonPath), () -> {
                                if (MockServerLogger.isEnabled(DEBUG)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(DEBUG)
                                            .setMessageFormat("expectation file watcher updating expectations as modification detected on file{}")
                                            .setArguments(configuration.initializationJsonPath())
                                    );
                                }
                                addExpectationsFromInitializer();
                            }, throwable -> {
                                if (MockServerLogger.isEnabled(WARN)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(WARN)
                                            .setMessageFormat("exception while processing expectation file update " + throwable.getMessage())
                                            .setThrowable(throwable)
                                    );
                                }
                            }, mockServerLogger);
                        } catch (Throwable throwable) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.ERROR)
                                    .setMessageFormat("exception creating file watcher for{}")
                                    .setArguments(initializationJsonPath)
                                    .setThrowable(throwable)
                            );
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception creating file watchers for{}")
                        .setArguments(initializationJsonPaths)
                        .setThrowable(throwable)
                );
            }
            if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("created expectation file watcher for{}")
                        .setArguments(initializationJsonPaths)
                );
            }
        } else {
            this.expectationSerializer = null;
            this.mockServerLogger = null;
            this.requestMatchers = null;
            this.expectationInitializerLoader = null;
        }
    }

    private synchronized void addExpectationsFromInitializer() {
        expectationInitializerLoader.retrieveExpectationsFromFile("", "exception while loading JSON initialization file with file watcher, ignoring file:{}", "updating expectations:{}from file:{}", Cause.Type.FILE_INITIALISER);
    }

    public void stop() {
        if (fileWatchers != null) {
            fileWatchers.forEach(fileWatcher -> fileWatcher.setRunning(false));
        }
    }
}
