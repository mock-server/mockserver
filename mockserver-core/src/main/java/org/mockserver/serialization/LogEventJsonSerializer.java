package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class LogEventJsonSerializer implements Serializer<MessageLogEntry> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public LogEventJsonSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(MessageLogEntry messageLogEntry) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(messageLogEntry);
        } catch (Exception e) {
            mockServerLogger.error(String.format("Exception while serializing messageLogEntry to JSON with value %s", messageLogEntry), e);
            throw new RuntimeException(String.format("Exception while serializing messageLogEntry to JSON with value %s", messageLogEntry), e);
        }
    }

    public String serialize(List<MessageLogEntry> messageLogEntries) {
        return serialize(messageLogEntries.toArray(new MessageLogEntry[messageLogEntries.size()]));
    }

    public String serialize(MessageLogEntry... messageLogEntries) {
        try {
            if (messageLogEntries != null && messageLogEntries.length > 0) {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(messageLogEntries);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.error("Exception while serializing MessageLogEntry to JSON with value " + Arrays.asList(messageLogEntries), e);
            throw new RuntimeException("Exception while serializing MessageLogEntry to JSON with value " + Arrays.asList(messageLogEntries), e);
        }
    }

    public MessageLogEntry deserialize(String jsonMessageLogEntry) {
        MessageLogEntry messageLogEntry = null;
        if (jsonMessageLogEntry != null && !jsonMessageLogEntry.isEmpty()) {
            try {
                messageLogEntry = objectMapper.readValue(jsonMessageLogEntry, MessageLogEntry.class);
            } catch (Exception e) {
                mockServerLogger.error((HttpRequest) null, e, "exception while parsing {}for MessageLogEntry", jsonMessageLogEntry);
                throw new RuntimeException("Exception while parsing MessageLogEntry for [" + jsonMessageLogEntry + "]", e);
            }
        }
        return messageLogEntry;
    }

    @Override
    public Class<MessageLogEntry> supportsType() {
        return MessageLogEntry.class;
    }
}
