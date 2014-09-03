package org.mockserver.registration.model;

import java.util.UUID;
import org.mockserver.model.EqualsHashCodeToString;

/**
 * A response containing service information from a registered service.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationResponse extends EqualsHashCodeToString {

    private UUID serviceId;
    private String serviceName;
    private String serviceAddress;

    public ServiceRegistrationResponse() {
    }

    public ServiceRegistrationResponse setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public UUID getServiceId() {
        return this.serviceId;
    }

    public ServiceRegistrationResponse setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public ServiceRegistrationResponse setServiceAddress(String serviceAddress) {
        this.serviceAddress = serviceAddress;
        return this;
    }

    public String getServiceAddress() {
        return this.serviceAddress;
    }
}
