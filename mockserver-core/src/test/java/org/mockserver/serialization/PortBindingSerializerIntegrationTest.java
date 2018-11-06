package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "    \"ports\": [" + NEW_LINE +
                "        0," + NEW_LINE +
                "        1080," + NEW_LINE +
                "        0" + NEW_LINE +
                "    ]," + NEW_LINE +
                "    \"extra_field\": \"extra_value\"" + NEW_LINE +
                "}";

        // when
        PortBinding portBinding = new PortBindingSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(portBinding(0, 1080, 0), portBinding);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "    \"ports\": [" + NEW_LINE +
                "        0," + NEW_LINE +
                "        1080," + NEW_LINE +
                "        0" + NEW_LINE +
                "    ]" + NEW_LINE +
                "}";

        // when
        PortBinding portBinding = new PortBindingSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(portBinding(0, 1080, 0), portBinding);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{ }";

        // when
        PortBinding portBinding = new PortBindingSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(portBinding(), portBinding);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonPortBinding = new PortBindingSerializer(new MockServerLogger()).serialize(
                new PortBinding().setPorts(Arrays.asList(0, 1080, 0))
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"ports\" : [ 0, 1080, 0 ]" + NEW_LINE +
                "}", jsonPortBinding);
    }
}
