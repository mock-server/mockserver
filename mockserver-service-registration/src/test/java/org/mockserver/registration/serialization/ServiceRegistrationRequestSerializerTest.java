package org.mockserver.registration.serialization;

import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.registration.model.ServiceRegistrationRequest;

import static org.junit.Assert.assertEquals;

/**
 * Tests for registration request serializer.
 *
 * @author nayyara.samuel
 * @since 3.7
 */
public class ServiceRegistrationRequestSerializerTest {

    private ServiceRegistrationRequestSerializer requestSerializer = new ServiceRegistrationRequestSerializer();
    private UUID serviceId = UUID.randomUUID();
    private String serviceName = "myService-v1";
    private String requestJson = "{" + System.getProperty("line.separator") +
            "  \"serviceId\" : \"" + serviceId + "\"," + System.getProperty("line.separator") +
            "  \"serviceName\" : \"" + serviceName + "\"" + System.getProperty("line.separator") +
            "}";
    private ServiceRegistrationRequest request = new ServiceRegistrationRequest();

    @Before
    public void setUp() {
        this.request.setServiceId(this.serviceId);
        this.request.setServiceName(this.serviceName);
    }

    @Test
    public void testSerialization() {
        assertEquals(this.requestJson, this.requestSerializer.serialize(this.request));
    }

    @Test
    public void testDeserialization() {
        assertEquals(this.request, this.requestSerializer.deserialize(this.requestJson));
    }
}
