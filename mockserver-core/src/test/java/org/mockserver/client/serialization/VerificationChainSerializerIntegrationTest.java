package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.VerificationChainDTO;
import org.mockserver.verify.VerificationChain;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class VerificationChainSerializerIntegrationTest {

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
        VerificationChain verificationChain = new VerificationChainSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationChainDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one"))
                ))
                .buildObject(), verificationChain);
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
        VerificationChain verificationChain = new VerificationChainSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationChainDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one").withBody("some_body_one")),
                        new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple")),
                        new HttpRequestDTO(request("some_path_three").withBody("some_body_three")),
                        new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"))
                ))
                .buildObject(), verificationChain);
    }

    @Test
    public void shouldDeserializeEmptyObject() throws IOException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "    \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "}";

        // when
        VerificationChain verificationChain = new VerificationChainSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationChainDTO()
                .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                .buildObject(), verificationChain);
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
        VerificationChain verificationChain = new VerificationChainSerializer().deserialize(requestBytes);

        // then
        assertEquals(new VerificationChainDTO()
                .setHttpRequests(Arrays.asList(
                        new HttpRequestDTO(request("some_path_one"))
                ))
                .buildObject(), verificationChain);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new VerificationChainSerializer().serialize(
                new VerificationChainDTO()
                        .setHttpRequests(Arrays.asList(
                                new HttpRequestDTO(request("some_path_one").withBody("some_body_one")),
                                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple")),
                                new HttpRequestDTO(request("some_path_three").withBody("some_body_three")),
                                new HttpRequestDTO(request("some_body_multiple").withBody("some_body_multiple"))
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
        String jsonExpectation = new VerificationChainSerializer().serialize(
                new VerificationChainDTO()
                        .setHttpRequests(Arrays.asList(
                                new HttpRequestDTO(request("some_path_one").withBody("some_body_one"))
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
        String jsonExpectation = new VerificationChainSerializer().serialize(
                new VerificationChainDTO()
                        .setHttpRequests(Arrays.<HttpRequestDTO>asList())
                        .buildObject()
        );

        // then
        assertEquals("{ }", jsonExpectation);
    }
}
