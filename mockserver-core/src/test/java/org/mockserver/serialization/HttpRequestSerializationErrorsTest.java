package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpRequestSerializer httpRequestSerializer;


    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new HttpRequestSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing HttpRequest to JSON with value { }");
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
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldHandleNullAndEmptyWhileSerializingArray() {
        // when
        assertEquals("[]", httpRequestSerializer.serialize(new HttpRequest[]{}));
        assertEquals("[]", httpRequestSerializer.serialize((HttpRequest[]) null));
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() {
        try {
            // when
            httpRequestSerializer.deserialize("requestBytes");
            fail("expected exception to be thrown");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("JsonParseException - Unrecognized token 'requestBytes': was expecting (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"requestBytes\"; line: 1, column: 13]"));
        }
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() {
        // when
        try {
            httpRequestSerializer.deserializeArray("requestBytes");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'requestBytes': was expecting (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"requestBytes\"; line: 1, column: 13]"));
        }
    }

    @Test
    public void shouldValidateInputForArray() {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:" + NEW_LINE +
                " - a request or request array is required but value was \"\"");

        // when
        assertArrayEquals(new HttpRequest[]{}, httpRequestSerializer.deserializeArray(""));
    }
}
