package org.mockserver.server.initialize;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.cache.LRUCache;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.persistence.ExpectationFileWatcher;
import org.mockserver.serialization.ExpectationSerializer;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoader {

    private static final LRUCache<String, List<String>> EXPANDED_INITIALIZATION_JSON_PATHS = new LRUCache<>(new MockServerLogger(LRUCache.class), 10, TimeUnit.HOURS.toMillis(1));
    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;

    public ExpectationInitializerLoader(MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
        this.requestMatchers = requestMatchers;
        addExpectationsFromInitializer();
    }

    public static List<String> expandedInitializationJsonPaths() {
        List<String> expandedInitializationJsonPaths = EXPANDED_INITIALIZATION_JSON_PATHS.get(ConfigurationProperties.initializationJsonPath());
        if (expandedInitializationJsonPaths == null) {
            expandedInitializationJsonPaths = FileReader.expandFilePathGlobs(ConfigurationProperties.initializationJsonPath());
            EXPANDED_INITIALIZATION_JSON_PATHS.put(ConfigurationProperties.initializationJsonPath(), expandedInitializationJsonPaths);
        }
        return expandedInitializationJsonPaths;
    }

    private void addExpectationsFromInitializer() {
        for (Expectation expectation : loadExpectations()) {
            requestMatchers.add(expectation, Cause.INITIALISER);
        }
    }

    private Expectation[] retrieveExpectationsFromInitializerClass() {
        try {
            String initializationClass = ConfigurationProperties.initializationClass();
            if (isNotBlank(initializationClass)) {
                if (MockServerLogger.isEnabled(INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(INFO)
                            .setMessageFormat("loading class initialization file:{}")
                            .setArguments(initializationClass)
                    );
                }
                ClassLoader contextClassLoader = ExpectationInitializerLoader.class.getClassLoader();
                if (contextClassLoader != null && isNotBlank(initializationClass)) {
                    Constructor<?> initializerClassConstructor = contextClassLoader.loadClass(initializationClass).getDeclaredConstructor();
                    Object expectationInitializer = initializerClassConstructor.newInstance();
                    if (expectationInitializer instanceof ExpectationInitializer) {
                        return ((ExpectationInitializer) expectationInitializer).initializeExpectations();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new Expectation[0];
    }

    @SuppressWarnings("FuseStreamOperations")
    private Expectation[] retrieveExpectationsFromJson() {
        List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths();
        List<Expectation> collect = initializationJsonPaths
            .stream()
            .flatMap(initializationJsonPath -> {
                if (isNotBlank(initializationJsonPath)) {
                    if (MockServerLogger.isEnabled(INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(SERVER_CONFIGURATION)
                                .setLogLevel(INFO)
                                .setMessageFormat("loading JSON initialization file:{}")
                                .setArguments(initializationJsonPath)
                        );
                    }
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

    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        return ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson);
    }
}
