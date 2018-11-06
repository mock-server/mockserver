package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializerIntegrationTest {

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
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
    public void shouldDeserializeEmptyObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"httpRequests\" : [ ]" + NEW_LINE +
                "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
                .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
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
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one"))
                ))
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
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
    public void shouldSerializePartialObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSequenceSerializer(new MockServerLogger()).serialize(
                new VerificationSequenceDTO()
                        .setHttpRequests(Arrays.asList(
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
    public void shouldSerializeEmptyObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSequenceSerializer(new MockServerLogger()).serialize(
                new VerificationSequenceDTO()
                        .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                        .buildObject()
        );

        // then
        assertEquals("{ }", jsonExpectation);
    }
}
