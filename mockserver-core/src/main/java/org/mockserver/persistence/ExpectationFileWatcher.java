package org.mockserver.persistence;

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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
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
                            return new FileWatcher(Paths.get(initializationJsonPath), () -> {
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
        }
    }

    private synchronized void addExpectationsFromInitializer() {
        ExpectationInitializerLoader
            .expandedInitializationJsonPaths()
            .forEach(initializationJsonPath -> {
                Expectation[] expectations = new Expectation[0];
                if (isNotBlank(initializationJsonPath)) {
                    try {
                        String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                        if (isNotBlank(jsonExpectations)) {
                            expectations = expectationSerializer.deserializeArray(jsonExpectations, true);
                        }
                    } catch (Throwable throwable) {
                        if (MockServerLogger.isEnabled(WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(SERVER_CONFIGURATION)
                                    .setLogLevel(WARN)
                                    .setMessageFormat("exception while loading JSON initialization file with file watcher, ignoring file:{}")
                                    .setArguments(initializationJsonPath)
                                    .setThrowable(throwable)
                            );
                        }
                    }
                }
                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("updating expectations:{}from file:{}")
                            .setArguments(Arrays.asList(expectations), initializationJsonPath)
                    );
                }
                requestMatchers.update(expectations, new MockServerMatcherNotifier.Cause(initializationJsonPath, MockServerMatcherNotifier.Cause.Type.FILE_WATCHER));
            });
    }

    public void stop() {
        if (fileWatchers != null) {
            fileWatchers.forEach(fileWatcher -> fileWatcher.setRunning(false));
        }
    }
}
