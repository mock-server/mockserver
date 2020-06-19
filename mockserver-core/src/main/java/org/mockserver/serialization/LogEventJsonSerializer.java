package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class LogEventJsonSerializer implements Serializer<LogEntry> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public LogEventJsonSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(LogEntry messageLogEntry) {
        try {
            return objectWriter.writeValueAsString(messageLogEntry);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing messageLogEntry to JSON with value " + messageLogEntry)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing messageLogEntry to JSON with value " + messageLogEntry, e);
        }
    }

    public String serialize(List<LogEntry> messageLogEntries) {
        return serialize(messageLogEntries.toArray(new LogEntry[0]));
    }

    public String serialize(LogEntry... messageLogEntries) {
        try {
            if (messageLogEntries != null && messageLogEntries.length > 0) {
                return objectWriter.writeValueAsString(messageLogEntries);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing LogEntry to JSON with value " + Arrays.asList(messageLogEntries))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing LogEntry to JSON with value " + Arrays.asList(messageLogEntries), e);
        }
    }

    public LogEntry deserialize(String jsonLogEntry) {
        LogEntry messageLogEntry = null;
        if (jsonLogEntry != null && !jsonLogEntry.isEmpty()) {
            try {
                messageLogEntry = objectMapper.readValue(jsonLogEntry, LogEntry.class);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing{}for LogEntry " + throwable.getMessage())
                        .setArguments(jsonLogEntry)
                        .setThrowable(throwable)
                );
                throw new RuntimeException("Exception while parsing LogEntry for [" + jsonLogEntry + "]", throwable);
            }
        }
        return messageLogEntry;
    }

    @Override
    public Class<LogEntry> supportsType() {
        return LogEntry.class;
    }
}
