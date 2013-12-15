package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerIntegrationTest {

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"method\": \"someMethod\",\n" +
                "        \"url\": \"someURL\",\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"queryString\": \"someQueryString\",\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"headers\": [\n" +
                "            {\n" +
                "                \"name\": \"someHeaderName\",\n" +
                "                \"values\": [\"someHeaderValue\"]\n" +
                "            }\n" +
                "        ],\n" +
                "        \"cookies\": [\n" +
                "            {\n" +
                "                \"name\": \"someCookieName\",\n" +
                "                \"values\": [\"someCookieValue\"]\n" +
                "            }\n" +
                "        ]\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"statusCode\": 304,\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"headers\": [\n" +
                "            {\n" +
                "                \"name\": \"someHeaderName\",\n" +
                "                \"values\": [\"someHeaderValue\"]\n" +
                "            }\n" +
                "        ],\n" +
                "        \"cookies\": [\n" +
                "            {\n" +
                "                \"name\": \"someCookieName\",\n" +
                "                \"values\": [\"someCookieValue\"]\n" +
                "            }\n" +
                "        ],\n" +
                "        \"delay\": {\n" +
                "            \"timeUnit\": \"MICROSECONDS\",\n" +
                "            \"value\": 1\n" +
                "        }\n" +
                "    },\n" +
                "    \"times\": {\n" +
                "        \"remainingTimes\": 5,\n" +
                "        \"unlimited\": false\n" +
                "    }\n" +
                "}").getBytes();

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod("someMethod")
                                .setURL("someURL")
                                .setPath("somePath")
                                .setQueryString("someQueryString")
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(TimeUnit.MICROSECONDS)
                                                .setValue(1)))
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation);
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    }\n" +
                "}").getBytes();

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod("someMethod")
                                .setURL("someURL")
                                .setPath("somePath")
                                .setQueryString("someQueryString")
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(TimeUnit.MICROSECONDS)
                                                .setValue(1)))
                .setTimes(new TimesDTO(Times.exactly(5)))
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"someURL\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryString\" : \"someQueryString\",\n" +
                "    \"body\" : \"someBody\",\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 304,\n" +
                "    \"body\" : \"someBody\",\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 1\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 5,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialRequestAndResponse() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"somePath\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"body\" : \"someBody\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 5,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectation() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"somePath\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"body\" : \"someBody\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 1,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}", jsonExpectation);
    }
}
