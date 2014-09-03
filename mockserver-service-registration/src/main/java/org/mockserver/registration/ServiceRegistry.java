package org.mockserver.registration;

import java.util.List;
import org.mockserver.registration.model.ServiceRegistrationRequest;
import org.mockserver.registration.model.ServiceRegistrationResponse;

/**
 * Interface for service registration.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public interface ServiceRegistry {

    /**
     * Register the service with the given parameters in the registry
     *
     * @param serviceRegistrationRequest - request for registration
     * @return response from the service registration
     * @throws Exception
     */
    public ServiceRegistrationResponse register(ServiceRegistrationRequest serviceRegistrationRequest) throws Exception;

    /**
     * Unregister the service with the given parameters in the registry
     *
     * @param serviceRegistrationRequest - request for registration
     * @return response from the service unregistration
     * @throws Exception
     */
    public ServiceRegistrationResponse unregister(ServiceRegistrationRequest serviceRegistrationRequest) throws Exception;

    /**
     * List all services in the registry with the given name (if provided).
     *
     * @param serviceName - name of the service to query
     * @throws Exception
     * @response list of service instances found
     */
    public List<ServiceRegistrationResponse> registry(String serviceName) throws Exception;

    /** Method to start the registry and reserve resources  */
    public void start();

    /** Method to start the registry and release any held resources  */
    public void stop();
}
