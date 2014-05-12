package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactoryTest {


    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String json = ("{\n" +
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
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

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
                .setTimes(new TimesDTO(Times.exactly(5))), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParametersBody() throws IOException {
        // given
        String json = ("{\n" +
                "    \"httpRequest\": {\n" +
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
                "    }\n" +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBody() throws IOException {
        // given
        String json = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"EXACT\",\n" +
                "            \"value\" : \"some_value\"\n" +
                "        }\n" +
                "    }\n" +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value", Body.Type.EXACT)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexStringBody() throws IOException {
        // given
        String json = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"REGEX\",\n" +
                "            \"value\" : \"some[a-zA-Z]*\"\n" +
                "        }\n" +
                "    }\n" +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some[a-zA-Z]*", Body.Type.REGEX)))
                ), expectationDTO);
    }


    @Test
    public void shouldParseJSONWithXPathStringBody() throws IOException {
        // given
        String json = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"XPATH\",\n" +
                "            \"value\" : \"\\\\some\\\\xpath\"\n" +
                "        }\n" +
                "    }\n" +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("\\some\\xpath", Body.Type.XPATH)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBody() throws IOException {
        // given
        String json = ("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"body\" : {\n" +
                "            \"type\" : \"BINARY\",\n" +
                "            \"value\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"\n" +
                "        }\n" +
                "    }\n" +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody(DatatypeConverter.printBase64Binary("some_value".getBytes()).getBytes())))
                ), expectationDTO);
    }
}
