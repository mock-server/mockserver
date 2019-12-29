package org.mockserver.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.serialization.serializers.response.TimeToLiveSerializer;
import org.mockserver.ui.MockServerMatcherListener;
import org.mockserver.ui.MockServerMatcherNotifier;
import org.slf4j.event.Level;

import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;

public class ExpectationFileSystemPersistence implements MockServerMatcherListener {

    private final ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private final Path filePath;

    public ExpectationFileSystemPersistence(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        if (ConfigurationProperties.persistExpectations()) {
            this.mockServerLogger = mockServerLogger;
            this.objectMapper = createObjectMapper(new TimeToLiveSerializer());
            this.filePath = Paths.get(ConfigurationProperties.persistedExpectationsPath());
            try {
                Files.createFile(filePath);
            } catch (FileAlreadyExistsException ignore) {
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Exception creating persisted expectations file " + filePath.toString())
                        .setThrowable(throwable)
                );
            }
            mockServerMatcher.registerListener(this);
        } else {
            this.mockServerLogger = null;
            this.objectMapper = null;
            this.filePath = null;
        }
    }

    @Override
    public void updated(MockServerMatcher mockServerLog, MockServerMatcherNotifier.Cause cause) {
        if (cause == MockServerMatcherNotifier.Cause.API) {
            try {
                Files.write(filePath, serialize(mockServerLog.retrieveActiveExpectations(null)).getBytes(UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Exception while persisting expectations to " + filePath.toString())
                        .setThrowable(throwable)
                );
            }
        }
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[0]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(expectations);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations), e);
        }
    }
}
