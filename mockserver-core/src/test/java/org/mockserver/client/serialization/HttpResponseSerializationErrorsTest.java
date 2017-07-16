package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpResponse;

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
public class HttpResponseSerializationErrorsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpResponseSerializer httpResponseSerializer;


    @Before
    public void setupTestFixture() {
        httpResponseSerializer = spy(new HttpResponseSerializer());

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing httpResponse to JSON with value { }");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpResponseDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.serialize(new HttpResponse());
    }

    @Test
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing HttpResponse to JSON with value [{ }]");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpResponseDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.serialize(new HttpResponse[]{new HttpResponse()});
    }

    @Test
    public void shouldHandleNullAndEmptyWhileSerializingArray() throws IOException {
        // when
        assertEquals("", httpResponseSerializer.serialize(new HttpResponse[]{}));
        assertEquals("", httpResponseSerializer.serialize((HttpResponse[]) null));
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("JsonParseException - Unrecognized token 'responseBytes': was expecting ('true', 'false' or 'null')");

        // when
        httpResponseSerializer.deserialize("responseBytes");
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'responseBytes': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: responseBytes; line: 1, column: 27]");

        // when
        httpResponseSerializer.deserializeArray("responseBytes");
    }

    @Test
    public void shouldValidateInputForArray() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:\n" +
                " - a response or response array is required but value was \"\"");

        // when
        assertArrayEquals(new HttpResponse[]{}, httpResponseSerializer.deserializeArray(""));
    }

}
