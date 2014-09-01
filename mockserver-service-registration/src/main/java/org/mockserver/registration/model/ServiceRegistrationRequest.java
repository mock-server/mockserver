package org.mockserver.registration.model;

import java.util.UUID;
import org.mockserver.model.EqualsHashCodeToString;

/**
 * Request to register a service
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationRequest extends EqualsHashCodeToString {

    protected UUID serviceId;
    private String serviceName;

    public ServiceRegistrationRequest() {
        this.serviceId = UUID.randomUUID();
    }

    public ServiceRegistrationRequest setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public UUID getServiceId() {
        return this.serviceId;
    }

    public ServiceRegistrationRequest setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getServiceName() {
        return this.serviceName;
    }
}
