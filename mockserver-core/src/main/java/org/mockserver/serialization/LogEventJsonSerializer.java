package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public LogEventJsonSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(LogEntry messageLogEntry) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(messageLogEntry);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while serializing messageLogEntry to JSON with value " + messageLogEntry)
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
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(messageLogEntries);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while serializing LogEntry to JSON with value " + Arrays.asList(messageLogEntries))
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
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing {} for LogEntry")
                        .setArguments(jsonLogEntry)
                        .setThrowable(e)
                );
                throw new RuntimeException("Exception while parsing LogEntry for [" + jsonLogEntry + "]", e);
            }
        }
        return messageLogEntry;
    }

    @Override
    public Class<LogEntry> supportsType() {
        return LogEntry.class;
    }
}
