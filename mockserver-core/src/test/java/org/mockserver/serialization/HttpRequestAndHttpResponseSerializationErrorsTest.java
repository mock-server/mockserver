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
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.serialization.model.HttpRequestAndHttpResponseDTO;

import java.io.IOException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponseSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpRequestAndHttpResponseSerializer httpRequestSerializer;


    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new HttpRequestAndHttpResponseSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        when(objectWriter.writeValueAsString(any(HttpRequestAndHttpResponseDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        try {
            // when
            httpRequestSerializer.serialize(new HttpRequestAndHttpResponse());
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable, instanceOf(RuntimeException.class));
            assertThat(throwable.getMessage(), is("Exception while serializing HttpRequestAndHttpResponse to JSON with value { }"));
        }
    }

    @Test
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        when(objectWriter.writeValueAsString(any(HttpRequestAndHttpResponseDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        try {
            // when
            httpRequestSerializer.serialize(new HttpRequestAndHttpResponse[]{new HttpRequestAndHttpResponse()});
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable, instanceOf(RuntimeException.class));
            assertThat(throwable.getMessage(), is("Exception while serializing HttpRequestAndHttpResponse to JSON with value [{ }]"));
        }
    }

    @Test
    public void shouldHandleNullAndEmptyWhileSerializingArray() {
        // when
        assertEquals("[]", httpRequestSerializer.serialize());
        assertEquals("[]", httpRequestSerializer.serialize((HttpRequestAndHttpResponse[]) null));
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
        try {
            // when
            assertArrayEquals(new HttpRequestAndHttpResponse[]{}, httpRequestSerializer.deserializeArray(""));
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable, instanceOf(IllegalArgumentException.class));
            assertThat(throwable.getMessage(), is("1 error:" + NEW_LINE +
                " - a request or request array is required but value was \"\""));
        }
    }
}
