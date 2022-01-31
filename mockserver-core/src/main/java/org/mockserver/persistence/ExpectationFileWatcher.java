package org.mockserver.persistence;

import org.mockserver.cache.LRUCache;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

public class ExpectationFileWatcher {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;
    private List<FileWatcher> fileWatchers;

    public ExpectationFileWatcher(MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        if (ConfigurationProperties.watchInitializationJson()) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
            this.mockServerLogger = mockServerLogger;
            this.requestMatchers = requestMatchers;
            List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths();
            try {
                fileWatchers = initializationJsonPaths
                    .stream()
                    .map(initializationJsonPath -> {
                        try {
                            return new FileWatcher(initializationJsonPath, () -> {
                                if (MockServerLogger.isEnabled(DEBUG)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(DEBUG)
                                            .setMessageFormat("expectation file watcher updating expectations as modification detected on file{}")
                                            .setArguments(ConfigurationProperties.initializationJsonPath())
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
                            });
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
        }
    }

    private synchronized void addExpectationsFromInitializer() {
        Expectation[] expectations = retrieveExpectationsFromJson();
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMessageFormat("updating expectations{}from{}")
                    .setArguments(Arrays.asList(expectations), ExpectationInitializerLoader.expandedInitializationJsonPaths())
            );
        }
        requestMatchers.update(expectations, MockServerMatcherNotifier.Cause.FILE_WATCHER);
    }

    @SuppressWarnings("FuseStreamOperations")
    private Expectation[] retrieveExpectationsFromJson() {
        List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths();
        List<Expectation> collect = initializationJsonPaths
            .stream()
            .flatMap(initializationJsonPath -> {
                if (isNotBlank(initializationJsonPath)) {
                    try {
                        String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                        if (isNotBlank(jsonExpectations)) {
                            return Arrays.stream(expectationSerializer.deserializeArray(jsonExpectations, true));
                        }
                    } catch (Throwable throwable) {
                        if (MockServerLogger.isEnabled(WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(SERVER_CONFIGURATION)
                                    .setLogLevel(WARN)
                                    .setMessageFormat("exception while loading JSON initialization file with file watcher, ignoring file")
                                    .setThrowable(throwable)
                            );
                        }
                    }
                }
                return Arrays.stream(new Expectation[0]);
            })
            .collect(Collectors.toList());
        return collect.toArray(new Expectation[0]);
    }

    public void stop() {
        if (fileWatchers != null) {
            fileWatchers.forEach(fileWatcher -> fileWatcher.setRunning(false));
        }
    }
}
