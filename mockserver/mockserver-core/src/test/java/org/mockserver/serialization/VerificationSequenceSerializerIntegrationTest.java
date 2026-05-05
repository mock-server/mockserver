package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.verify.VerificationSequence;

import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializerIntegrationTest {

    @Test
    public void shouldDeserializeCompleteObjectWithRequests() {
        // given
        String requestBytes = "{" + NEW_LINE +
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

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
            .setHttpRequests(Arrays.asList(
                new HttpRequestDTO(request("some_path_one").withBody("some_body_one")),
                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple")),
                new HttpRequestDTO(request("some_path_three").withBody("some_body_three")),
                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"))
            ))
            .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithExpectationIds() {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"expectationIds\" : [ {" + NEW_LINE +
            "    \"id\" : \"one\"" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"id\" : \"two\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
            .setExpectationIds(Arrays.asList(
                new ExpectationId().withId("one"),
                new ExpectationId().withId("two")
            ))
            .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializeEmptyObject() {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"httpRequests\" : [ ]" + NEW_LINE +
            "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
            .setHttpRequests(Collections.emptyList())
            .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializePartialObject() {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"httpRequests\" : [ {" + NEW_LINE +
            "    \"path\" : \"some_path_one\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
            .setHttpRequests(Collections.singletonList(
                new HttpRequestDTO(request("some_path_one"))
            ))
            .buildObject(), verificationSequence);
    }

    @Test
    public void shouldSerializeCompleteObject() {
        // when
        String jsonExpectation = new VerificationSequenceSerializer(new MockServerLogger()).serialize(
            new VerificationSequenceDTO()
                .setHttpRequests(Arrays.asList(
                    new HttpRequestDTO(request("some_path_one").withBody("some_body_one")),
                    new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple")),
                    new HttpRequestDTO(request("some_path_three").withBody("some_body_three")),
                    new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"))
                ))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
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
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialObject() {
        // when
        String jsonExpectation = new VerificationSequenceSerializer(new MockServerLogger()).serialize(
            new VerificationSequenceDTO()
                .setHttpRequests(Collections.singletonList(
                    new HttpRequestDTO(request("some_path_one").withBody("some_body_one"))
                ))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"httpRequests\" : [ {" + NEW_LINE +
            "    \"path\" : \"some_path_one\"," + NEW_LINE +
            "    \"body\" : \"some_body_one\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeEmptyObject() {
        // when
        String jsonExpectation = new VerificationSequenceSerializer(new MockServerLogger()).serialize(
            new VerificationSequenceDTO()
                .setHttpRequests(Collections.emptyList())
                .buildObject()
        );

        // then
        assertEquals("{ }", jsonExpectation);
    }
}
