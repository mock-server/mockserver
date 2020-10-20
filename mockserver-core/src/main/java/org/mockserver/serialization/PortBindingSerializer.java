package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.PortBinding;
import org.slf4j.event.Level;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializer implements Serializer<PortBinding> {
    private final MockServerLogger mockServerLogger;
    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public PortBindingSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(PortBinding portBinding) {
        try {
            return objectWriter.writeValueAsString(portBinding);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing portBinding to JSON with value " + portBinding)
                    .setThrowable(throwable)
            );
            throw new RuntimeException("Exception while serializing portBinding to JSON with value " + portBinding, throwable);
        }
    }

    public PortBinding deserialize(String jsonPortBinding) {
        PortBinding portBinding = null;
        if (jsonPortBinding != null && !jsonPortBinding.isEmpty()) {
            try {
                portBinding = objectMapper.readValue(jsonPortBinding, PortBinding.class);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing{}for PortBinding " + throwable.getMessage())
                        .setArguments(jsonPortBinding)
                        .setThrowable(throwable)
                );
                throw new  IllegalArgumentException("exception while parsing PortBinding for [" + jsonPortBinding + "]", throwable);
            }
        }
        return portBinding;
    }

    @Override
    public Class<PortBinding> supportsType() {
        return PortBinding.class;
    }
}
