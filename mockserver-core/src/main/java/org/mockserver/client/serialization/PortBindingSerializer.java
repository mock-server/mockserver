package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.PortBinding;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializer implements Serializer<PortBinding> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public PortBindingSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public String serialize(PortBinding portBinding) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(portBinding);
        } catch (Exception e) {
            mockServerLogger.error(String.format("Exception while serializing portBinding to JSON with value %s", portBinding), e);
            throw new RuntimeException(String.format("Exception while serializing portBinding to JSON with value %s", portBinding), e);
        }
    }

    public PortBinding deserialize(String jsonPortBinding) {
        PortBinding portBinding = null;
        if (jsonPortBinding != null && !jsonPortBinding.isEmpty()) {
            try {
                portBinding = objectMapper.readValue(jsonPortBinding, PortBinding.class);
            } catch (Exception e) {
                mockServerLogger.error("Exception while parsing PortBinding for [" + jsonPortBinding + "]", e);
                throw new RuntimeException("Exception while parsing PortBinding for [" + jsonPortBinding + "]", e);
            }
        }
        return portBinding;
    }

    @Override
    public Class<PortBinding> supportsType() {
        return PortBinding.class;
    }
}
