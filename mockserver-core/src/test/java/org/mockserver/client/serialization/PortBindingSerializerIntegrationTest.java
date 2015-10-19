package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"ports\": [" + System.getProperty("line.separator") +
                "        0," + System.getProperty("line.separator") +
                "        1080," + System.getProperty("line.separator") +
                "        0" + System.getProperty("line.separator") +
                "    ]," + System.getProperty("line.separator") +
                "    \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "}";

        // when
        PortBinding portBinding = new PortBindingSerializer().deserialize(requestBytes);

        // then
        assertEquals(portBinding(0, 1080, 0), portBinding);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"ports\": [" + System.getProperty("line.separator") +
                "        0," + System.getProperty("line.separator") +
                "        1080," + System.getProperty("line.separator") +
                "        0" + System.getProperty("line.separator") +
                "    ]" + System.getProperty("line.separator") +
                "}";

        // when
        PortBinding portBinding = new PortBindingSerializer().deserialize(requestBytes);

        // then
        assertEquals(portBinding(0, 1080, 0), portBinding);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{ }";

        // when
        PortBinding portBinding = new PortBindingSerializer().deserialize(requestBytes);

        // then
        assertEquals(portBinding(), portBinding);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonPortBinding = new PortBindingSerializer().serialize(
                new PortBinding().setPorts(Arrays.asList(0, 1080, 0))
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"ports\" : [ 0, 1080, 0 ]" + System.getProperty("line.separator") +
                "}", jsonPortBinding);
    }
}
