package org.mockserver.server.initialize;

import org.apache.commons.lang3.ArrayUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.ui.MockServerMatcherNotifier.Cause;

import java.lang.reflect.Constructor;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoader {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;

    public ExpectationInitializerLoader(MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
        this.requestMatchers = requestMatchers;
        addExpectationsFromInitializer();
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
                ClassLoader contextClassLoader = ExpectationInitializerLoader.class.getClassLoader();
                if (contextClassLoader != null && isNotEmpty(initializationClass)) {
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

    private Expectation[] retrieveExpectationsFromJson() {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        if (isNotBlank(initializationJsonPath)) {
            try {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                if (isNotBlank(jsonExpectations)) {
                    return expectationSerializer.deserializeArray(jsonExpectations, true);
                } else {
                    return new Expectation[0];
                }
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(WARN)
                            .setMessageFormat("exception while loading JSON initialization file, ignoring file")
                            .setThrowable(throwable)
                    );
                }
            }
        }
        return new Expectation[0];
    }

    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        return ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson);
    }
}
