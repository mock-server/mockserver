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
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;

import java.io.IOException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpResponseSerializer httpResponseSerializer;


    @Before
    public void setupTestFixture() {
        httpResponseSerializer = spy(new HttpResponseSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing httpResponse to JSON with value { }");
        // and
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
        when(objectWriter.writeValueAsString(any(HttpResponseDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.serialize(new HttpResponse[]{new HttpResponse()});
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldHandleNullAndEmptyWhileSerializingArray() {
        // when
        assertEquals("[]", httpResponseSerializer.serialize(new HttpResponse[]{}));
        assertEquals("[]", httpResponseSerializer.serialize((HttpResponse[]) null));
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() {
        try {
            // when
            httpResponseSerializer.deserialize("responseBytes");
            fail("expected exception to be thrown");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect response json format for:" + NEW_LINE +
                "" + NEW_LINE +
                "  responseBytes" + NEW_LINE +
                "" + NEW_LINE +
                " schema validation errors:" + NEW_LINE +
                "" + NEW_LINE +
                "  JsonParseException - Unrecognized token 'responseBytes': was expecting (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                "   at [Source: (String)\"responseBytes\"; line: 1, column: 14]"));
        }
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() {
        try {
            // when
            httpResponseSerializer.deserializeArray("responseBytes");
            fail("expected exception to be thrown");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'responseBytes': was expecting (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"responseBytes\"; line: 1, column: 14]"));
        }
    }

    @Test
    public void shouldValidateInputForArray() {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:" + NEW_LINE +
            " - a response or response array is required but value was \"\"");

        // when
        assertArrayEquals(new HttpResponse[]{}, httpResponseSerializer.deserializeArray(""));
    }

}
