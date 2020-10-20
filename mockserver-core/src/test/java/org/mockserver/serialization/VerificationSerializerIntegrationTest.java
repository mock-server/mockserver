package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.VerificationDTO;
import org.mockserver.serialization.model.VerificationTimesDTO;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSerializerIntegrationTest {

    @Test
    public void shouldDeserializeCompleteObject() {
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
    public void shouldDeserializePartialObject() {
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
    public void shouldSerializeCompleteObject() {
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
    public void shouldSerializePartialObject() {
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
