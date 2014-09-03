package org.mockserver.registration.curator;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.mockserver.registration.ServiceRegistry;
import org.mockserver.registration.model.ServiceRegistrationRequest;
import org.mockserver.registration.model.ServiceRegistrationResponse;

import static org.mockserver.registration.curator.AddressUtils.getLocalHostAddress;
import static org.mockserver.registration.curator.AddressUtils.toZookeeperConnectionString;
import static org.mockserver.registration.curator.AddressUtils.COLON_JOINER;

/**
 * Class the handles service registration via Curator and stores registry with Zookeeper.
 * Nodes are stored as PERSISTENT and must be deleted from the registry with the unregister() method.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class CuratorServiceRegistry implements ServiceRegistry {

    private final InetSocketAddress mockServerAddress;
    private ServiceDiscovery<Void> serviceDiscovery;
    private CuratorFramework curatorFramework;

    private final ConcurrentMap<String, ServiceCache<Void>> serviceCaches = new ConcurrentHashMap();

    private static final int SESSION_TIMEOUT_IN_MS = 6000;
    //connection timeout should always be <= session timeout
    //https://issues.apache.org/jira/browse/CURATOR-8
    private static final int CONNECTION_TIMEOUT_IN_MS = 6000;
    private static final int TIME_RETRIES_IN_MS = 1000;

    // Funtion to convert from a ServiceInstance to a ServiceRegistrationResponse
    private static final Function<ServiceInstance, ServiceRegistrationResponse> TO_RESPONSE_MAPPER =
            new Function<ServiceInstance, ServiceRegistrationResponse>() {
                @Override
                public ServiceRegistrationResponse apply(ServiceInstance serviceInstance) {
                    if (serviceInstance == null) {
                        return null;
                    }
                    ServiceRegistrationResponse serviceRegistrationResponse = new ServiceRegistrationResponse();
                    serviceRegistrationResponse.setServiceAddress(COLON_JOINER.join(serviceInstance.getAddress(), serviceInstance.getPort()))
                            .setServiceId(UUID.fromString(serviceInstance.getId()))
                            .setServiceName(serviceInstance.getName());

                    return serviceRegistrationResponse;
                }
            };

    public CuratorServiceRegistry(CuratorRegistryConfiguration configuration) throws Exception {
        this.mockServerAddress = getLocalHostAddress(configuration.getMockserverPort());

        this.curatorFramework = CuratorFrameworkFactory.newClient(
                toZookeeperConnectionString(configuration.getZookeeperAddresses()),
                SESSION_TIMEOUT_IN_MS,
                CONNECTION_TIMEOUT_IN_MS,
                new RetryNTimes(5, TIME_RETRIES_IN_MS)
        );

        this.serviceDiscovery = ServiceDiscoveryBuilder.builder(Void.class)
                .client(curatorFramework)
                .basePath(configuration.getZookeeperBasePath())
                .build();
    }

    @Override
    public ServiceRegistrationResponse register(ServiceRegistrationRequest serviceRegistrationRequest) throws Exception {
        ServiceInstance<Void> serviceInstance = ServiceInstance.<Void>builder()
                .id(serviceRegistrationRequest.getServiceId().toString())
                .address(this.mockServerAddress.getAddress().getHostAddress())
                .port(this.mockServerAddress.getPort())
                .name(serviceRegistrationRequest.getServiceName())
                .serviceType(ServiceType.STATIC)
                .build();

        this.serviceDiscovery.registerService(serviceInstance);

        //Create a service cache for the service we just registered so we can use it later.
        serviceCache(serviceRegistrationRequest.getServiceName());

        return fromServiceInstance(serviceInstance);
    }

    @Override
    public ServiceRegistrationResponse unregister(ServiceRegistrationRequest serviceRegistrationRequest) throws Exception {
        List<ServiceInstance<Void>> instances = serviceCache(serviceRegistrationRequest.getServiceName()).getInstances();
        ServiceInstance<Void> foundInstance = null;
        for (ServiceInstance<Void> cachedInstance : instances) {
            if (cachedInstance.getId().equals(serviceRegistrationRequest.getServiceId().toString())) {
                //Unregister the service instance previously registered
                foundInstance = cachedInstance;
                this.serviceDiscovery.unregisterService(foundInstance);
            }
        }
        return fromServiceInstance(foundInstance);
    }

    @Override
    public List<ServiceRegistrationResponse> registry(String serviceName) throws Exception {
        ImmutableList.Builder<ServiceInstance<?>> registryInstances = ImmutableList.builder();
        if (serviceName != null) {
            registryInstances.addAll(serviceCache(serviceName).getInstances());
        } else {
            for (String cachedServiceName : this.serviceCaches.keySet()) {
                registryInstances.addAll(serviceCache(cachedServiceName).getInstances());
            }
        }

        return fromServiceInstances(registryInstances.build());
    }

    //Retrieve cache of the named service
    private ServiceCache<Void> serviceCache(String serviceName) throws Exception {
        synchronized (this.serviceCaches) {
            ServiceCache<Void> serviceCache = this.serviceCaches.get(serviceName);
            if (serviceCache == null) {
                serviceCache = this.serviceDiscovery.serviceCacheBuilder().name(serviceName).build();
                serviceCache.start();
                this.serviceCaches.putIfAbsent(serviceName, serviceCache);
            }
            return serviceCache;
        }

    }

    // Convert a ServiceInstance to the ServiceRegistrationResponse
    private static ServiceRegistrationResponse fromServiceInstance(ServiceInstance<?> serviceInstance) {
        return TO_RESPONSE_MAPPER.apply(serviceInstance);
    }

    // Convert a collection of type ServiceInstance to the ServiceRegistrationResponse collection
    private static List<ServiceRegistrationResponse> fromServiceInstances(List<ServiceInstance<?>> serviceInstances) {
        return ImmutableList.copyOf(Collections2.transform(serviceInstances, TO_RESPONSE_MAPPER));
    }

    @Override
    public void start() {
        try {
            this.curatorFramework.start();
            this.serviceDiscovery.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        try {
            this.curatorFramework.close();
            this.serviceDiscovery.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
