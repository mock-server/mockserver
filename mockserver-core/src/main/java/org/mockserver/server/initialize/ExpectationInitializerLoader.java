package org.mockserver.server.initialize;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.ArrayUtils;
import org.mockserver.cache.LRUCache;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
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
        retrieveExpectationsFromJson();
        for (Expectation expectation : retrieveExpectationsFromInitializerClass()) {
            requestMatchers.add(expectation, new Cause("", Cause.Type.CLASS_INITIALISER));
        }
    }

    private Expectation[] retrieveExpectationsFromInitializerClass() {
        Expectation[] expectations = new Expectation[0];
        String initializationClass = ConfigurationProperties.initializationClass();
        try {
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
                        expectations = ((ExpectationInitializer) expectationInitializer).initializeExpectations();
                    }
                }
            }
            if (expectations.length > 0) {
                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("loaded expectations:{}from class:{}")
                            .setArguments(Arrays.asList(expectations), initializationClass)
                    );
                }
                requestMatchers.update(expectations, new MockServerMatcherNotifier.Cause(initializationClass, Cause.Type.CLASS_INITIALISER));
            }
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while loading JSON initialization class, ignoring class:{}")
                        .setArguments(initializationClass)
                        .setThrowable(throwable)
                );
            }
        }
        return expectations;
    }

    @SuppressWarnings("FuseStreamOperations")
    private Expectation[] retrieveExpectationsFromJson() {
        List<String> initializationJsonPaths = ExpectationInitializerLoader.expandedInitializationJsonPaths();
        List<Expectation> collect = initializationJsonPaths
            .stream()
            .flatMap(initializationJsonPath -> {
                Expectation[] expectations = new Expectation[0];
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
                            expectations = expectationSerializer.deserializeArray(jsonExpectations, true);
                        }
                    } catch (Throwable throwable) {
                        if (MockServerLogger.isEnabled(WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(SERVER_CONFIGURATION)
                                    .setLogLevel(WARN)
                                    .setMessageFormat("exception while loading JSON initialization file, ignoring file:{}")
                                    .setArguments(initializationJsonPath)
                                    .setThrowable(throwable)
                            );
                        }
                    }
                }
                if (expectations.length > 0) {
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setMessageFormat("loaded expectations:{}from file:{}")
                                .setArguments(Arrays.asList(expectations), initializationJsonPath)
                        );
                    }
                    requestMatchers.update(expectations, new MockServerMatcherNotifier.Cause(initializationJsonPath, Cause.Type.FILE_INITIALISER));
                }
                return Arrays.stream(expectations);
            })
            .collect(Collectors.toList());
        return collect.toArray(new Expectation[0]);
    }

    @VisibleForTesting
    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        return ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson);
    }
}
