package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.CookieDTO;
import org.mockserver.client.serialization.model.HeaderDTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.ParameterDTO;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"path\": \"somePath\",\n" +
                "    \"extra_field\": \"extra_value\"\n" +
                "}").getBytes();

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"method\": \"someMethod\",\n" +
                "    \"url\": \"someURL\",\n" +
                "    \"path\": \"somePath\",\n" +
                "    \"queryString\": \"someQueryString\",\n" +
                "    \"parameters\" : [ {\n" +
                "        \"name\" : \"parameterName\",\n" +
                "        \"values\" : [ \"parameterValue\" ]\n" +
                "    } ],\n" +
                "    \"body\": \"someBody\",\n" +
                "    \"headers\": [\n" +
                "        {\n" +
                "            \"name\": \"someHeaderName\",\n" +
                "            \"values\": [\"someHeaderValue\"]\n" +
                "        }\n" +
                "    ],\n" +
                "    \"cookies\": [\n" +
                "        {\n" +
                "            \"name\": \"someCookieName\",\n" +
                "            \"values\": [\"someCookieValue\"]\n" +
                "        }\n" +
                "    ]\n" +
                "}").getBytes();

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setMethod("someMethod")
                .setURL("someURL")
                .setPath("somePath")
                .setQueryString("someQueryString")
                .setParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("parameterName", Arrays.asList("parameterValue")))))
                .setBody("someBody")
                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"path\": \"somePath\"\n" +
                "}").getBytes();

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setMethod("someMethod")
                        .setURL("someURL")
                        .setPath("somePath")
                        .setQueryString("someQueryString")
                        .setParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("parameterName", Arrays.asList("parameterValue")))))
                        .setBody("someBody")
                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                        .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"method\" : \"someMethod\",\n" +
                "  \"url\" : \"someURL\",\n" +
                "  \"path\" : \"somePath\",\n" +
                "  \"queryString\" : \"someQueryString\",\n" +
                "  \"parameters\" : [ {\n" +
                "    \"name\" : \"parameterName\",\n" +
                "    \"values\" : [ \"parameterValue\" ]\n" +
                "  } ],\n" +
                "  \"body\" : \"someBody\",\n" +
                "  \"cookies\" : [ {\n" +
                "    \"name\" : \"someCookieName\",\n" +
                "    \"values\" : [ \"someCookieValue\" ]\n" +
                "  } ],\n" +
                "  \"headers\" : [ {\n" +
                "    \"name\" : \"someHeaderName\",\n" +
                "    \"values\" : [ \"someHeaderValue\" ]\n" +
                "  } ]\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialRequestAndResponse() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"path\" : \"somePath\"\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectation() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"path\" : \"somePath\"\n" +
                "}", jsonExpectation);
    }
}
