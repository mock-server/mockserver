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
import org.mockserver.serialization.model.VerificationDTO;
import org.mockserver.verify.Verification;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class VerificationSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationSerializer verificationSerializer;


    @Before
    public void setupTestFixture() {
        verificationSerializer = spy(new VerificationSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing verification to JSON with value {" + NEW_LINE +
            "  \"httpRequest\" : { }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"atLeast\" : 1" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");
        // and
        when(objectWriter.writeValueAsString(any(VerificationDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        verificationSerializer.serialize(new Verification());
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() {
        try {
            // when
            verificationSerializer.deserialize("requestBytes");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect verification json format for:" + NEW_LINE +
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
    public void shouldHandleExceptionWhileDeserializingObjectWithExpectationIdsAndRequests() {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"expectationId\" : {" + NEW_LINE +
            "    \"id\" : \"one\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"some_path_one\"," + NEW_LINE +
            "    \"body\" : \"some_body_one\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";
        try {
            // when
            new VerificationSerializer(new MockServerLogger()).deserialize(requestBytes);
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect verification json format for:" + NEW_LINE +
                "" + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"expectationId\" : {" + NEW_LINE +
                "      \"id\" : \"one\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"some_path_one\"," + NEW_LINE +
                "      \"body\" : \"some_body_one\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }" + NEW_LINE +
                "" + NEW_LINE +
                " schema validation errors:" + NEW_LINE +
                "" + NEW_LINE +
                "  1 error:" + NEW_LINE +
                "   - instance failed to match exactly one schema (matched 2 out of 2)" + NEW_LINE +
                "  " + NEW_LINE +
                "  " + OPEN_API_SPECIFICATION_URL));
        }
    }

}
