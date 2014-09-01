package org.mockserver.registration.curator;

import com.google.common.collect.ImmutableSet;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.registration.model.ServiceRegistrationRequest;
import org.mockserver.registration.model.ServiceRegistrationResponse;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.registration.curator.AddressUtils.toStandardAddressScheme;

/**
 * Tests for CuratorService registry.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class CuratorServiceRegistryTest {
    //Zookeeper parameters
    private static final String ZOOKEEPER_HOST = "127.0.0.1";
    private static final int ZOOKEEPER_PORT = 2181;
    private static final String ZOOKEEPER_BASE_PATH = "/services/stage";
    private static final Set<InetSocketAddress> ZOOKEEPER_ADDRESSES = ImmutableSet.<InetSocketAddress>builder()
            .add(new InetSocketAddress(ZOOKEEPER_HOST, ZOOKEEPER_PORT)).build();
    private static final InetSocketAddress MOCKSERVER_ADDRESS = new InetSocketAddress(ZOOKEEPER_HOST, 2000);

    private TestingServer zkTestServer;
    private CuratorServiceRegistry serviceRegistry;

    private UUID serviceId;
    private String serviceName;
    private ServiceRegistrationResponse serviceRegistration = new ServiceRegistrationResponse();

    @Before
    public void startUpZookeeper() throws Exception {
        this.zkTestServer = new TestingServer(ZOOKEEPER_PORT);
        this.serviceId = UUID.randomUUID();
        this.serviceName = "myService-v1";

        this.serviceRegistration.setServiceId(serviceId);
        this.serviceRegistration.setServiceName(serviceName);
        this.serviceRegistration.setServiceAddress(toStandardAddressScheme(MOCKSERVER_ADDRESS));

        //Create configuration for the curator service registry
        CuratorRegistryConfiguration curatorRegistryConfiguration = new CuratorRegistryConfiguration()
                .setMockserverAddress(MOCKSERVER_ADDRESS)
                .setZookeeperAddresses(ZOOKEEPER_ADDRESSES)
                .setZookeeperBasePath(ZOOKEEPER_BASE_PATH);
        this.serviceRegistry = new CuratorServiceRegistry(curatorRegistryConfiguration);
        this.serviceRegistry.start();
    }

    @Test
    public void serviceRegistrationAndUnregistrationWork() throws Exception {
        ServiceRegistrationRequest registrationRequest = new ServiceRegistrationRequest()
                .setServiceId(this.serviceId)
                .setServiceName(this.serviceName);

        this.serviceRegistry.register(registrationRequest);
        assertTrue(this.serviceRegistry.registry(serviceName).contains(this.serviceRegistration));

        this.serviceRegistry.unregister(registrationRequest);

        // Service caches refresh every so often, wait for service cache to catch up
        Thread.sleep(1000);
        assertFalse(this.serviceRegistry.registry(serviceName).contains(this.serviceRegistration));
    }

    @After
    public void shutDownZookeeper() throws Exception {
        this.serviceRegistry.stop();
        this.zkTestServer.stop();
    }
}
