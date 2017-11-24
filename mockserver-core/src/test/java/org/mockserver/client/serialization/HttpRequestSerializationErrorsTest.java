package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.HttpRequest;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializationErrorsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpRequestSerializer httpRequestSerializer;


    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new HttpRequestSerializer());

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing httpRequest to JSON with value { }");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpRequestDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpRequestSerializer.serialize(new HttpRequest());
    }

    @Test
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing HttpRequest to JSON with value [{ }]");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpRequestDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpRequestSerializer.serialize(new HttpRequest[]{new HttpRequest()});
    }

    @Test
    public void shouldHandleNullAndEmptyWhileSerializingArray() throws IOException {
        // when
        assertEquals("", httpRequestSerializer.serialize(new HttpRequest[]{}));
        assertEquals("", httpRequestSerializer.serialize((HttpRequest[]) null));
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("JsonParseException - Unrecognized token 'requestBytes': was expecting ('true', 'false' or 'null')");

        // when
        httpRequestSerializer.deserialize("requestBytes");
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'requestBytes': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: (String)\"requestBytes\"; line: 1, column: 25]");

        // when
        httpRequestSerializer.deserializeArray("requestBytes");
    }

    @Test
    public void shouldValidateInputForArray() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:\n" +
                " - a request or request array is required but value was \"\"");

        // when
        assertArrayEquals(new HttpRequest[]{}, httpRequestSerializer.deserializeArray(""));
    }
}
