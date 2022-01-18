package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private OpenAPIExpectationSerializer httpRequestSerializer;


    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new OpenAPIExpectationSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing expectation to JSON with value { }");
        // and
        when(objectWriter.writeValueAsString(any(OpenAPIExpectationDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpRequestSerializer.serialize(new OpenAPIExpectation());
    }

    @Test
    public void shouldHandleExceptionWhileSerializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing expectation to JSON with value [{ }]");
        // and
        when(objectWriter.writeValueAsString(any(OpenAPIExpectationDTO[].class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpRequestSerializer.serialize(new OpenAPIExpectation[]{new OpenAPIExpectation()});
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldHandleNullAndEmptyWhileSerializingArray() {
        // when
        assertEquals("[]", httpRequestSerializer.serialize(new OpenAPIExpectation[]{}));
        assertEquals("[]", httpRequestSerializer.serialize((OpenAPIExpectation[]) null));
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() {
        try {
            // when
            httpRequestSerializer.deserialize("requestBytes");
            fail("expected exception to be thrown");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect openapi expectation json format for:" + NEW_LINE +
                "" + NEW_LINE +
                "  requestBytes" + NEW_LINE +
                "" + NEW_LINE +
                " schema validation errors:" + NEW_LINE +
                "" + NEW_LINE +
                "  JsonParseException - Unrecognized token 'requestBytes': was expecting (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                "   at [Source: (String)\"requestBytes\"; line: 1, column: 13]"));
        }
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() {
        // when
        try {
            httpRequestSerializer.deserializeArray("requestBytes", true);
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
        thrown.expectMessage("1 error:" + NEW_LINE + " - an expectation or array of expectations is required");

        // when
        assertArrayEquals(new OpenAPIExpectation[]{}, httpRequestSerializer.deserializeArray("[]", false));
    }

    @Test
    public void shouldAllowEmptyArray() {
        // when
        assertArrayEquals(new OpenAPIExpectation[]{}, httpRequestSerializer.deserializeArray("[]", true));

        // then - no exception thrown
    }
}
