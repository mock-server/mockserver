package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Cookie;
import org.mockserver.model.Delay;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
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
    public void shouldIgnoreEmptyStringObjects() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": \"\"\n" +
                "}").getBytes();

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleNullPrimitives() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    },\n" +
                "    \"times\": {\n" +
                "        \"remainingTimes\": null,\n" +
                "        \"unlimited\": false\n" +
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
                .setTimes(new TimesDTO(Times.exactly(0)))
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleEmptyPrimitives() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    },\n" +
                "    \"times\": {\n" +
                "        \"remainingTimes\": \"\",\n" +
                "        \"unlimited\": false\n" +
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
                .setTimes(new TimesDTO(Times.exactly(0)))
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleNullEnums() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"delay\": {\n" +
                "            \"timeUnit\": null,\n" +
                "            \"value\": null\n" +
                "        }\n" +
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
                                .setDelay(new DelayDTO(new Delay(null, 0)))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldAllowSingleObjectForArray() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    }\n" +
                "}").getBytes();

        // when
        Expectation[] expectations = new ExpectationSerializer().deserializeArray(requestBytes);

        // then
        assertArrayEquals(new Expectation[]{
                new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath("somePath")
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody("someBody")
                        )
                        .buildObject()
        }, expectations);
    }


    @Test
    public void shouldAllowMultipleObjectsForArray() throws IOException {
        // given
        byte[] requestBytes = ("[" +
                "  {\n" +
                "      \"httpRequest\": {\n" +
                "          \"path\": \"somePath\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      },\n" +
                "      \"httpResponse\": {\n" +
                "          \"body\": \"someBody\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      }\n" +
                "  }," +
                "  {\n" +
                "      \"httpRequest\": {\n" +
                "          \"path\": \"somePath\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      },\n" +
                "      \"httpResponse\": {\n" +
                "          \"body\": \"someBody\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      }\n" +
                "  }," +
                "  {\n" +
                "      \"httpRequest\": {\n" +
                "          \"path\": \"somePath\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      },\n" +
                "      \"httpResponse\": {\n" +
                "          \"body\": \"someBody\",\n" +
                "          \"extra_field\": \"extra_value\"\n" +
                "      }\n" +
                "  }" +
                "]").getBytes();
        Expectation expectation = new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject();

        // when
        Expectation[] expectations = new ExpectationSerializer().deserializeArray(requestBytes);

        // then
        assertArrayEquals(new Expectation[]{
                expectation,
                expectation,
                expectation
        }, expectations);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        byte[] requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"method\": \"someMethod\",\n" +
                "        \"url\": \"http://www.example.com\",\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"queryString\": \"someQueryString\",\n" +
                "        \"parameters\" : [ {\n" +
                "            \"name\" : \"parameterName\",\n" +
                "            \"values\" : [ \"parameterValue\" ]\n" +
                "        } ]," +
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
                                .setURL("http://www.example.com")
                                .setPath("somePath")
                                .setQueryString("someQueryString")
                                .setParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("parameterName", Arrays.asList("parameterValue")))))
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
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
                                .setURL("http://www.example.com")
                                .setPath("somePath")
                                .setQueryString("someQueryString")
                                .setParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("parameterName", Arrays.asList("parameterValue")))))
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody("someBody")
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
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
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryString\" : \"someQueryString\",\n" +
                "    \"parameters\" : [ {\n" +
                "      \"name\" : \"parameterName\",\n" +
                "      \"values\" : [ \"parameterValue\" ]\n" +
                "    } ],\n" +
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


    @Test
    public void shouldSerializePartialExpectationArray() throws IOException {
        // when
        Expectation expectation = new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject();
        String jsonExpectation = new ExpectationSerializer().serialize(new Expectation[]{
                expectation,
                expectation,
                expectation
        });

        // then
        assertEquals("[ {\n" +
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
                "}, {\n" +
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
                "}, {\n" +
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
                "} ]", jsonExpectation);
    }
}
