package org.mockserver.server.persistence;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.ui.MockServerMatcherListener;
import org.slf4j.event.Level;

import java.nio.file.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ExpectationFileSystemPersistence implements MockServerMatcherListener {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final Path filePath;

    public ExpectationFileSystemPersistence(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
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
            Files.write(filePath, expectationSerializer.serialize(mockServerLog.retrieveActiveExpectations(null)).getBytes(UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
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
