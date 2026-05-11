package org.mockserver.persistence;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.listeners.MockServerLogListener;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.serialization.serializers.response.TimeToLiveDTOPersistenceSerializer;
import org.slf4j.event.Level;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;
import static org.slf4j.event.Level.*;

public class RecordedExpectationFileSystemPersistence implements MockServerLogListener {

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final MockServerEventLog mockServerEventLog;
    private final ObjectWriter objectWriter;
    private final Path filePath;
    private final ReentrantLock fileWriteLock = new ReentrantLock();

    public RecordedExpectationFileSystemPersistence(Configuration configuration, MockServerLogger mockServerLogger, MockServerEventLog mockServerEventLog) {
        this.configuration = configuration;
        if (configuration.persistRecordedExpectations()) {
            this.mockServerLogger = mockServerLogger;
            this.mockServerEventLog = mockServerEventLog;
            this.objectWriter = createObjectMapper(true, false, new TimeToLiveDTOPersistenceSerializer());
            this.filePath = Paths.get(configuration.persistedRecordedExpectationsPath());
            try {
                Files.createFile(filePath);
            } catch (FileAlreadyExistsException ignore) {
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception creating persisted recorded expectations file " + filePath)
                        .setThrowable(throwable)
                );
            }
            mockServerEventLog.registerListener(this);
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("created recorded expectation file system persistence for{}")
                        .setArguments(configuration.persistedRecordedExpectationsPath())
                );
            }
        } else {
            this.mockServerLogger = null;
            this.mockServerEventLog = null;
            this.objectWriter = null;
            this.filePath = null;
        }
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        if (mockServerEventLog == null) {
            return;
        }
        CompletableFuture<List<Expectation>> future = new CompletableFuture<>();
        mockServerLog.retrieveRecordedExpectations(null, future::complete);
        try {
            List<Expectation> expectations = future.get(30, TimeUnit.SECONDS);
            writeToFile(expectations);
        } catch (Exception e) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while retrieving recorded expectations for persistence")
                        .setThrowable(e)
                );
            }
        }
    }

    private void writeToFile(List<Expectation> expectations) {
        fileWriteLock.lock();
        try {
            try (
                FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile());
                FileChannel fileChannel = fileOutputStream.getChannel();
                FileLock fileLock = fileChannel.lock()
            ) {
                if (fileLock != null) {
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setMessageFormat("persisting recorded expectations{}to{}")
                                .setArguments(expectations, configuration.persistedRecordedExpectationsPath())
                        );
                    } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("persisting recorded expectations to{}")
                                .setArguments(configuration.persistedRecordedExpectationsPath())
                        );
                    }
                    byte[] data = serialize(expectations).getBytes(UTF_8);
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    buffer.put(data);
                    buffer.rewind();
                    while (buffer.hasRemaining()) {
                        fileChannel.write(buffer);
                    }
                }
            }
        } catch (Throwable throwable) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while persisting recorded expectations to " + filePath.toString())
                        .setThrowable(throwable)
                );
            }
        } finally {
            fileWriteLock.unlock();
        }
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[0]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                ExpectationDTO[] expectationDTOs = new ExpectationDTO[expectations.length];
                for (int i = 0; i < expectations.length; i++) {
                    expectationDTOs[i] = new ExpectationDTO(expectations[i]);
                }
                return objectWriter.writeValueAsString(expectationDTOs);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while serializing recorded expectation to JSON with value " + Arrays.asList(expectations))
                        .setThrowable(e)
                );
            }
            throw new RuntimeException("Exception while serializing recorded expectation to JSON with value " + Arrays.asList(expectations), e);
        }
    }

    public void stop() {
        if (mockServerEventLog != null) {
            mockServerEventLog.unregisterListener(this);
        }
    }
}
