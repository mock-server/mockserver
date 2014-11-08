package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.VerificationDTO;
import org.mockserver.client.serialization.model.VerificationTimesDTO;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationSerializerIntegrationTest {

    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somepath\"" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}";

        // when
        Verification expectation = new VerificationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationDTO()
                .setHttpRequest(new HttpRequestDTO(request().withPath("somepath")))
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"GET\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somepath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"count\" : 2," + System.getProperty("line.separator") +
                "    \"exact\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}";

        // when
        Verification expectation = new VerificationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationDTO()
                .setHttpRequest(new HttpRequestDTO(request().withMethod("GET").withPath("somepath")))
                .setTimes(new VerificationTimesDTO(VerificationTimes.exactly(2)))
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "}";

        // when
        Verification expectation = new VerificationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationDTO()
                .setHttpRequest(new HttpRequestDTO(request()))
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSerializer().serialize(
                new VerificationDTO()
                        .setHttpRequest(new HttpRequestDTO(request().withMethod("GET").withPath("somepath")))
                        .setTimes(new VerificationTimesDTO(VerificationTimes.exactly(2)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"GET\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somepath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"count\" : 2," + System.getProperty("line.separator") +
                "    \"exact\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialObject() throws IOException {
        // when
        String jsonExpectation = new VerificationSerializer().serialize(
                new VerificationDTO()
                        .setHttpRequest(new HttpRequestDTO(request()))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : { }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"count\" : 1," + System.getProperty("line.separator") +
                "    \"exact\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }
}
