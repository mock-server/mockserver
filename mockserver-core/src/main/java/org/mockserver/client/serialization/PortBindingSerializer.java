package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.PortBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(PortBinding portBinding) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(portBinding);
        } catch (Exception e) {
            logger.error(String.format("Exception while serializing portBinding to JSON with value %s", portBinding), e);
            throw new RuntimeException(String.format("Exception while serializing portBinding to JSON with value %s", portBinding), e);
        }
    }

    public PortBinding deserialize(String jsonPortBinding) {
        PortBinding portBinding = null;
        if (jsonPortBinding != null && !jsonPortBinding.isEmpty()) {
            try {
                portBinding = objectMapper.readValue(jsonPortBinding, PortBinding.class);
            } catch (Exception e) {
                logger.info("Exception while parsing PortBinding for [" + jsonPortBinding + "]", e);
                throw new RuntimeException("Exception while parsing PortBinding for [" + jsonPortBinding + "]", e);
            }
        }
        return portBinding;
    }
}
