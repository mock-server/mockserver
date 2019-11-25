package org.mockserver.server.initialize;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.serialization.ExpectationSerializer;

import java.lang.reflect.Constructor;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoader {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;

    public ExpectationInitializerLoader(MockServerLogger mockServerLogger) {
        expectationSerializer = new ExpectationSerializer(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
    }

    private Expectation[] retrieveExpectationsFromInitializerClass() {
        try {
            String initializationClass = ConfigurationProperties.initializationClass();
            if (isNotBlank(initializationClass)) {
                ClassLoader contextClassLoader = ExpectationInitializerLoader.class.getClassLoader();
                if (contextClassLoader != null && StringUtils.isNotEmpty(initializationClass)) {
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

    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        return ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson);
    }
}
