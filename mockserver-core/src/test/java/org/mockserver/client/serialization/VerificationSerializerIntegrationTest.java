package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.VerificationDTO;
import org.mockserver.client.serialization.model.VerificationTimesDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSerializerIntegrationTest {

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"somepath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"atLeast\" : 2," + NEW_LINE +
            "    \"atMost\" : 3" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        Verification verification = new VerificationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationDTO()
            .setHttpRequest(new HttpRequestDTO(request().withMethod("GET").withPath("somepath")))
            .setTimes(new VerificationTimesDTO(VerificationTimes.between(2, 3)))
            .buildObject(), verification);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        Verification verification = new VerificationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new VerificationDTO()
            .setHttpRequest(new HttpRequestDTO(request()))
            .buildObject(), verification);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSerializer(new MockServerLogger()).serialize(
            new VerificationDTO()
                .setHttpRequest(new HttpRequestDTO(request().withMethod("GET").withPath("somepath")))
                .setTimes(new VerificationTimesDTO(VerificationTimes.between(2, 3)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"somepath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"atLeast\" : 2," + NEW_LINE +
            "    \"atMost\" : 3" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSerializer(new MockServerLogger()).serialize(
            new VerificationDTO()
                .setHttpRequest(new HttpRequestDTO(request()))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"httpRequest\" : { }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"atLeast\" : 1," + NEW_LINE +
            "    \"atMost\" : 1" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }
}
