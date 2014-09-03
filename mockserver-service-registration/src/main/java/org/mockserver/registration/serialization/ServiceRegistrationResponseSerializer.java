package org.mockserver.registration.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.registration.model.ServiceRegistrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationResponseSerializer extends EqualsHashCodeToString {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public String serialize(ServiceRegistrationResponse serviceRegistrationResponse) {
        try {
            return this.objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(serviceRegistrationResponse);
        } catch (IOException ioe) {
            this.logger.error(String.format("Exception while serializing serviceRegistrationResponse to JSON with value %s", serviceRegistrationResponse), ioe);
            throw new RuntimeException(String.format("Exception while serializing serviceRegistrationResponse to JSON with value %s", serviceRegistrationResponse), ioe);
        }
    }

    public String serialize(List<ServiceRegistrationResponse> serviceRegistrationResponses) {
        try {
            return this.objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(serviceRegistrationResponses.toArray(new ServiceRegistrationResponse[]{}));
        } catch (IOException ioe) {
            this.logger.error(String.format("Exception while serializing serviceRegistrationResponse to JSON with value %s", serviceRegistrationResponses), ioe);
            throw new RuntimeException(String.format("Exception while serializing serviceRegistrationResponse to JSON with value %s", serviceRegistrationResponses), ioe);
        }
    }

    public ServiceRegistrationResponse deserialize(String jsonHttpRequest) {
        ServiceRegistrationResponse serviceRegistrationResponse = null;
        if (jsonHttpRequest != null && !jsonHttpRequest.isEmpty()) {
            try {
                serviceRegistrationResponse = this.objectMapper.readValue(jsonHttpRequest, ServiceRegistrationResponse.class);
            } catch (IOException ioe) {
                this.logger.info("Exception while parsing response [" + jsonHttpRequest + "] for http response serviceRegistrationResponse", ioe);
            }
        }
        return serviceRegistrationResponse;
    }
}
