package org.mockserver.registration.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.registration.model.ServiceRegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationRequestSerializer extends EqualsHashCodeToString {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(ServiceRegistrationRequest serviceRegistrationRequest) {
        try {
            return this.objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(serviceRegistrationRequest);
        } catch (IOException ioe) {
            this.logger.error(String.format("Exception while serializing serviceRegistrationRequest to JSON with value %s", serviceRegistrationRequest), ioe);
            throw new RuntimeException(String.format("Exception while serializing serviceRegistrationRequest to JSON with value %s", serviceRegistrationRequest), ioe);
        }
    }

    public ServiceRegistrationRequest deserialize(String jsonHttpRequest) {
        ServiceRegistrationRequest serviceRegistrationRequest = null;
        if (jsonHttpRequest != null && !jsonHttpRequest.isEmpty()) {
            try {
                serviceRegistrationRequest = this.objectMapper.readValue(jsonHttpRequest, ServiceRegistrationRequest.class);
            } catch (IOException ioe) {
                this.logger.info("Exception while parsing response [" + jsonHttpRequest + "] for http response serviceRegistrationRequest", ioe);
            }
        }
        return serviceRegistrationRequest;
    }
}
