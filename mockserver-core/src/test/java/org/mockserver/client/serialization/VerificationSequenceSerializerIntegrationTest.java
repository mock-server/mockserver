package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.VerificationSequenceDTO;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializerIntegrationTest {

    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"httpRequests\" : [ {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_one\"," + System.getProperty("line.separator") +
                "    \"random_field\" : \"random_value\"" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"random_field\" : \"random_value\"" + System.getProperty("line.separator") +
                "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one"), false)
                ))
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"httpRequests\" : [ {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_one\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_one\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_body_multiple\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_multiple\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_three\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_three\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_body_multiple\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_multiple\"" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one").withBody("some_body_one"), false),
                        new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"), false),
                        new HttpRequestDTO(request("some_path_three").withBody("some_body_three"), false),
                        new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"), false)
                ))
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializeEmptyObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
                .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"httpRequests\" : [ {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_one\"" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}";

        // when
        VerificationSequence verificationSequence = new VerificationSequenceSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationSequenceDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one"), false)
                ))
                .buildObject(), verificationSequence);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSequenceSerializer().serialize(
                new VerificationSequenceDTO()
                        .setHttpRequests(Arrays.asList(
                                new HttpRequestDTO(request("some_path_one").withBody("some_body_one"), false),
                                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"), false),
                                new HttpRequestDTO(request("some_path_three").withBody("some_body_three"), false),
                                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"), false)
                        ))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequests\" : [ {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_one\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_one\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_body_multiple\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_multiple\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_three\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_three\"" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_body_multiple\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_multiple\"" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSequenceSerializer().serialize(
                new VerificationSequenceDTO()
                        .setHttpRequests(Arrays.asList(
                                new HttpRequestDTO(request("some_path_one").withBody("some_body_one"), false)
                        ))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequests\" : [ {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path_one\"," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body_one\"" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeEmptyObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSequenceSerializer().serialize(
                new VerificationSequenceDTO()
                        .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                        .buildObject()
        );

        // then
        assertEquals("{ }", jsonExpectation);
    }
}
