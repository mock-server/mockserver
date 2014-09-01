package org.mockserver.registration.serialization;

import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.registration.model.ServiceRegistrationResponse;

import static org.junit.Assert.assertEquals;

/**
 * Tests for registration response serializer.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationResponseSerializerTest {

    private ServiceRegistrationResponseSerializer responseSerializer = new ServiceRegistrationResponseSerializer();
    private UUID serviceId = UUID.randomUUID();
    private String serviceName = "myService-v1";
    private InetSocketAddress serviceAddress = new InetSocketAddress("192.168.2.1", 7001);

    private String responseJson = "{" + System.getProperty("line.separator") +
            "  \"serviceId\" : \"" + serviceId + "\"," + System.getProperty("line.separator") +
            "  \"serviceName\" : \"" + serviceName + "\"," + System.getProperty("line.separator") +
            "  \"serviceAddress\" : \"" + serviceAddress + "\"" + System.getProperty("line.separator") +
            "}";
    private ServiceRegistrationResponse response = new ServiceRegistrationResponse();

    @Before
    public void setUp() {
        this.response.setServiceId(serviceId);
        this.response.setServiceName(serviceName);
        this.response.setServiceAddress(serviceAddress.toString());
    }

    @Test
    public void testSerialization() {
        assertEquals(this.responseJson, this.responseSerializer.serialize(this.response));
    }

    @Test
    public void testDeserialization() {
        assertEquals(this.response, this.responseSerializer.deserialize(this.responseJson));
    }
}
