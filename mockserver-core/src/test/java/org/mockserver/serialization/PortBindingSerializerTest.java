package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.PortBinding;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class PortBindingSerializerTest {

    private final PortBinding fullPortBinding = new PortBinding().setPorts(Arrays.asList(1, 2, 3, 4, 5));

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private PortBindingSerializer portBindingSerializer;

    @Before
    public void setupTestFixture() {
        portBindingSerializer = spy(new PortBindingSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(PortBinding.class))).thenReturn(fullPortBinding);

        // when
        PortBinding portBinding = portBindingSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullPortBinding, portBinding);
    }

    @Test
    public void deserializeHandleException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("exception while parsing PortBinding for [requestBytes]");
        // and
        when(objectMapper.readValue(eq("requestBytes"), same(PortBinding.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        portBindingSerializer.deserialize("requestBytes");
    }

    @Test
    public void serialize() throws IOException {
        // when
        portBindingSerializer.serialize(fullPortBinding);

        // then
        verify(objectWriter).writeValueAsString(fullPortBinding);
    }

    @Test
    public void serializeObjectHandlesException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(containsString("Exception while serializing portBinding to JSON with value"));
        // and
        when(objectWriter.writeValueAsString(any(PortBinding.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        portBindingSerializer.serialize(portBinding());
    }
}
