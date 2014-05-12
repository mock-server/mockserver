package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

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
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    }\n" +
                "}");

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
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": \"\"\n" +
                "}");

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
        String requestBytes = ("{\n" +
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
                "}");

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
        String requestBytes = ("{\n" +
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
                "}");

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
        String requestBytes = ("{\n" +
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
                "}");

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
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"extra_field\": \"extra_value\"\n" +
                "    }\n" +
                "}");

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
        String requestBytes = ("[" +
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
                "]");
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
    public void shouldDeserializeCompleteObjectWithResponse() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
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
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod("someMethod")
                                .setURL("http://www.example.com")
                                .setPath("somePath")
                                .setQueryStringParameters(Arrays.asList(
                                        new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                        new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                ))
                                .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.EXACT)))
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
                                                .setValue(1)
                                )
                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForward() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"httpForward\" : {\n" +
                "    \"host\" : \"someHost\",\n" +
                "    \"port\" : 1234,\n" +
                "    \"scheme\" : \"HTTPS\"" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 5,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod("someMethod")
                                        .setURL("http://www.example.com")
                                        .setPath("somePath")
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.EXACT)))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                        )
                        .setHttpForward(
                                new HttpForwardDTO()
                                        .setHost("someHost")
                                        .setPort(1234)
                                        .setScheme(HttpForward.Scheme.HTTPS)
                        )
                        .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    }\n" +
                "}");

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
    public void shouldDeserializeStringRegexBody() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"REGEX\",\n" +
                "            \"value\" : \"some[a-zA-Z]*\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    }\n" +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                                .setBody(new StringBodyDTO(new StringBody("some[a-zA-Z]*", Body.Type.REGEX)))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeParameterBody() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\",\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"PARAMETERS\",\n" +
                "            \"parameters\" : [ {\n" +
                "                    \"name\" : \"parameterOneName\",\n" +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]\n" +
                "                }, {\n" +
                "                    \"name\" : \"parameterTwoName\",\n" +
                "                    \"values\" : [ \"parameterTwoValue\" ]\n" +
                "            } ]\n" +
                "        }\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\"\n" +
                "    }\n" +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath("somePath")
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody("someBody")
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponse() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod("someMethod")
                                        .setURL("http://www.example.com")
                                        .setPath("somePath")
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.EXACT)))
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
                                                        .setValue(1)
                                        )
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
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
    public void shouldSerializeCompleteObjectWithForward() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod("someMethod")
                                        .setURL("http://www.example.com")
                                        .setPath("somePath")
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.EXACT)))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                        )
                        .setHttpForward(
                                new HttpForwardDTO()
                                        .setHost("someHost")
                                        .setPort(1234)
                                        .setScheme(HttpForward.Scheme.HTTPS)
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"httpForward\" : {\n" +
                "    \"host\" : \"someHost\",\n" +
                "    \"port\" : 1234,\n" +
                "    \"scheme\" : \"HTTPS\"\n" +
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
    public void shouldSerializeStringRegexBody() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath("somePath")
                                        .setBody(new StringBodyDTO(new StringBody("some[a-zA-Z]*", Body.Type.REGEX)))
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
                "    \"path\" : \"somePath\",\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"REGEX\",\n" +
                "      \"value\" : \"some[a-zA-Z]*\"\n" +
                "    }\n" +
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
    public void shouldSerializeStringParameterBody() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath("somePath")
                                        .setBody(new ParameterBodyDTO(new ParameterBody(
                                                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                                new Parameter("parameterTwoName", "parameterTwoValue")
                                        )))
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
                "    \"path\" : \"somePath\",\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"PARAMETERS\",\n" +
                "      \"parameters\" : [ {\n" +
                "        \"name\" : \"parameterOneName\",\n" +
                "        \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]\n" +
                "      }, {\n" +
                "        \"name\" : \"parameterTwoName\",\n" +
                "        \"values\" : [ \"parameterTwoValue\" ]\n" +
                "      } ]\n" +
                "    }\n" +
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
