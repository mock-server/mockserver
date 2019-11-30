package org.mockserver.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.serializers.response.TimeToLiveSerializer;
import org.mockserver.ui.MockServerMatcherListener;
import org.slf4j.event.Level;

import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;

public class ExpectationFileSystemPersistence implements MockServerMatcherListener {

    private ObjectMapper objectMapper = createObjectMapper(new TimeToLiveSerializer());
    private final MockServerLogger mockServerLogger;
    private final Path filePath;

    public ExpectationFileSystemPersistence(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.filePath = Paths.get(ConfigurationProperties.persistedExpectationsPath());
        if (ConfigurationProperties.persistExpectations()) {
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
        }
    }

    @Override
    public void updated(MockServerMatcher mockServerLog) {
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
