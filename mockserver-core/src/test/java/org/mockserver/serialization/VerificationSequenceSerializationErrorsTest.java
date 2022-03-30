package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializationErrorsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationSequenceSerializer verificationSequenceSerializer;


    @Before
    public void setupTestFixture() {
        verificationSequenceSerializer = spy(new VerificationSequenceSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing verificationSequence to JSON with value { }");
        // and
        when(objectWriter.writeValueAsString(any(VerificationSequenceDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        verificationSequenceSerializer.serialize(new VerificationSequence());
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() {
        try {
            // when
            verificationSequenceSerializer.deserialize("requestBytes");
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect verification sequence json format for:" + NEW_LINE +
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
            "  \"expectationIds\" : [ {" + NEW_LINE +
            "    \"id\" : \"one\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"id\" : \"two\"" + NEW_LINE +
            "  } ]," + NEW_LINE +
            "  \"httpRequests\" : [ {" + NEW_LINE +
            "    \"path\" : \"some_path_one\"," + NEW_LINE +
            "    \"body\" : \"some_body_one\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"path\" : \"some_body_multiple\"," + NEW_LINE +
            "    \"body\" : \"some_body_multiple\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"path\" : \"some_path_three\"," + NEW_LINE +
            "    \"body\" : \"some_body_three\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"path\" : \"some_body_multiple\"," + NEW_LINE +
            "    \"body\" : \"some_body_multiple\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";
        try {
            // when
            new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            // then
            assertThat(iae.getMessage(), is("incorrect verification sequence json format for:" + NEW_LINE +
                NEW_LINE +
                "  {" + NEW_LINE +
                "    \"expectationIds\" : [ {" + NEW_LINE +
                "      \"id\" : \"one\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"id\" : \"two\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"httpRequests\" : [ {" + NEW_LINE +
                "      \"path\" : \"some_path_one\"," + NEW_LINE +
                "      \"body\" : \"some_body_one\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"path\" : \"some_body_multiple\"," + NEW_LINE +
                "      \"body\" : \"some_body_multiple\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"path\" : \"some_path_three\"," + NEW_LINE +
                "      \"body\" : \"some_body_three\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"path\" : \"some_body_multiple\"," + NEW_LINE +
                "      \"body\" : \"some_body_multiple\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }" + NEW_LINE +
                NEW_LINE +
                " schema validation errors:" + NEW_LINE +
                "" + NEW_LINE +
                "  1 error:" + NEW_LINE +
                "   - $: should be valid to one and only one of schema, but more than one are valid: {\"required\":[\"expectationIds\"]}{\"required\":[\"httpRequests\"]}" + NEW_LINE +
                "  " + NEW_LINE +
                "  " + OPEN_API_SPECIFICATION_URL.replaceAll(NEW_LINE, NEW_LINE + "  " )));
        }
    }

}
