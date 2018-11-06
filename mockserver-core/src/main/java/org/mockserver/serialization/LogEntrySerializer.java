package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class LogEntrySerializer {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public LogEntrySerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(LogEntry logEntry) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(logEntry);
        } catch (Exception e) {
            mockServerLogger.error(String.format("Exception while serializing LogEntry to JSON with value %s", logEntry), e);
            throw new RuntimeException(String.format("Exception while serializing LogEntry to JSON with value %s", logEntry), e);
        }
    }

    public String serialize(List<LogEntry> logEntries) {
        return serialize(logEntries.toArray(new LogEntry[logEntries.size()]));
    }

    public String serialize(LogEntry... logEntries) {
        try {
            if (logEntries != null && logEntries.length > 0) {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(logEntries);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing LogEntry to JSON with value " + Arrays.asList(logEntries), e);
            throw new RuntimeException("Exception while serializing LogEntry to JSON with value " + Arrays.asList(logEntries), e);
        }
    }

}
