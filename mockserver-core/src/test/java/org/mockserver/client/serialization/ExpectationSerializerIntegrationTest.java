package org.mockserver.client.serialization;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "        \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "        \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldIgnoreEmptyStringObjects() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": \"\"" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleNullPrimitives() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"times\": {" + System.getProperty("line.separator") +
                "        \"remainingTimes\": null," + System.getProperty("line.separator") +
                "        \"unlimited\": false" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .setTimes(new TimesDTO(Times.exactly(0)))
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleEmptyPrimitives() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"times\": {" + System.getProperty("line.separator") +
                "        \"remainingTimes\": \"\"," + System.getProperty("line.separator") +
                "        \"unlimited\": false" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .setTimes(new TimesDTO(Times.exactly(0)))
                .buildObject(), expectation);
    }

    @Test
    public void shouldHandleNullEnums() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "        \"delay\": {" + System.getProperty("line.separator") +
                "            \"timeUnit\": null," + System.getProperty("line.separator") +
                "            \"value\": null" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setDelay(new DelayDTO(new Delay(null, 0)))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldAllowSingleObjectForArray() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "        \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "        \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation[] expectations = new ExpectationSerializer().deserializeArray(requestBytes);

        // then
        assertArrayEquals(new Expectation[]{
                new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .buildObject()
        }, expectations);
    }

    @Test
    public void shouldAllowMultipleObjectsForArray() throws IOException {
        // given
        String requestBytes = ("[" +
                "  {" + System.getProperty("line.separator") +
                "      \"httpRequest\": {" + System.getProperty("line.separator") +
                "          \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }," + System.getProperty("line.separator") +
                "      \"httpResponse\": {" + System.getProperty("line.separator") +
                "          \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }" + System.getProperty("line.separator") +
                "  }," +
                "  {" + System.getProperty("line.separator") +
                "      \"httpRequest\": {" + System.getProperty("line.separator") +
                "          \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }," + System.getProperty("line.separator") +
                "      \"httpResponse\": {" + System.getProperty("line.separator") +
                "          \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }" + System.getProperty("line.separator") +
                "  }," +
                "  {" + System.getProperty("line.separator") +
                "      \"httpRequest\": {" + System.getProperty("line.separator") +
                "          \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }," + System.getProperty("line.separator") +
                "      \"httpResponse\": {" + System.getProperty("line.separator") +
                "          \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "          \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
                "      }" + System.getProperty("line.separator") +
                "  }" +
                "]");
        Expectation expectation = new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod(string("someMethod"))
                                .setPath(string("somePath"))
                                .setQueryStringParameters(Arrays.asList(
                                        new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                        new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                ))
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpForward\" : {" + System.getProperty("line.separator") +
                "    \"host\" : \"someHost\"," + System.getProperty("line.separator") +
                "    \"port\" : 1234," + System.getProperty("line.separator") +
                "    \"scheme\" : \"HTTPS\"" +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod(string("someMethod"))
                                        .setPath(string("somePath"))
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        )
                        .setHttpForward(
                                new HttpForwardDTO()
                                        .setHost("someHost")
                                        .setPort(1234)
                                        .setScheme(HttpForward.Scheme.HTTPS)
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.unlimited())).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithCallback() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpCallback\" : {" + System.getProperty("line.separator") +
                "    \"callbackClass\" : \"someClass\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod(string("someMethod"))
                                        .setPath(string("somePath"))
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        )
                        .setHttpCallback(
                                new HttpCallbackDTO()
                                        .setCallbackClass("someClass")
                        )
                        .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeStringRegexBody() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                "            \"value\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                                .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeParameterBody() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        Expectation expectation = new ExpectationSerializer().deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponse() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod(string("someMethod"))
                                        .setPath(string("somePath"))
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setStatusCode(304)
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }


    @Test
    public void shouldSerializeCompleteObjectWithForward() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod(string("someMethod"))
                                        .setPath(string("somePath"))
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        )
                        .setHttpForward(
                                new HttpForwardDTO()
                                        .setHost("someHost")
                                        .setPort(1234)
                                        .setScheme(HttpForward.Scheme.HTTPS)
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpForward\" : {" + System.getProperty("line.separator") +
                "    \"host\" : \"someHost\"," + System.getProperty("line.separator") +
                "    \"port\" : 1234," + System.getProperty("line.separator") +
                "    \"scheme\" : \"HTTPS\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"HOURS\"," + System.getProperty("line.separator") +
                "    \"timeToLive\" : 2," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithCallback() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setMethod(string("someMethod"))
                                        .setPath(string("somePath"))
                                        .setQueryStringParameters(Arrays.asList(
                                                new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                                new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                        ))
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        )
                        .setHttpCallback(
                                new HttpCallbackDTO()
                                        .setCallbackClass("someClass")
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someCookieValue\"" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpCallback\" : {" + System.getProperty("line.separator") +
                "    \"callbackClass\" : \"someClass\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                        "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                        "    \"timeUnit\" : \"HOURS\"," + System.getProperty("line.separator") +
                        "    \"timeToLive\" : 2," + System.getProperty("line.separator") +
                        "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialRequestAndResponse() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"HOURS\"," + System.getProperty("line.separator") +
                "    \"timeToLive\" : 2," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringXPathBody() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                                        .setBody(new XPathBodyDTO(new XPathBody("/bookstore/book[price>35]/price")))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"XPATH\"," + System.getProperty("line.separator") +
                "      \"xpath\" : \"/bookstore/book[price>35]/price\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"HOURS\"," + System.getProperty("line.separator") +
                "    \"timeToLive\" : 2," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringJsonSchemaBody() throws IOException {
        // when
        String jsonSchema = "{" + System.getProperty("line.separator") +
                "  \"title\": \"Example Schema\"," + System.getProperty("line.separator") +
                "  \"type\": \"object\"," + System.getProperty("line.separator") +
                "  \"properties\": {" + System.getProperty("line.separator") +
                "    \"firstName\": {" + System.getProperty("line.separator") +
                "      \"type\": \"string\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"lastName\": {" + System.getProperty("line.separator") +
                "      \"type\": \"string\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"age\": {" + System.getProperty("line.separator") +
                "      \"description\": \"Age in years\"," + System.getProperty("line.separator") +
                "      \"type\": \"integer\"," + System.getProperty("line.separator") +
                "      \"minimum\": 0" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"required\": [\"firstName\", \"lastName\"]" + System.getProperty("line.separator") +
                "}";
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                                        .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema)))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}")))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"JSON_SCHEMA\"," + System.getProperty("line.separator") +
                "      \"jsonSchema\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"timeUnit\" : \"HOURS\"," + System.getProperty("line.separator") +
                "    \"timeToLive\" : 2," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringJsonBody() throws IOException {
        // when
        String jsonBody = "{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}";
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                                        .setBody(new JsonBodyDTO(new JsonBody(jsonBody)))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new JsonBodyDTO(new JsonBody(jsonBody)))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "      \"json\" : \"" + StringEscapeUtils.escapeJava(jsonBody) + "\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"" + StringEscapeUtils.escapeJava(jsonBody) + "\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringRegexBody() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                                        .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                "      \"regex\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringParameterBody() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                                        .setBody(new ParameterBodyDTO(new ParameterBody(
                                                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                                new Parameter("parameterTwoName", "parameterTwoValue")
                                        )))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .setTimes(new TimesDTO(Times.exactly(5)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "      \"parameters\" : [ {" + System.getProperty("line.separator") +
                "        \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "        \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "      }, {" + System.getProperty("line.separator") +
                "        \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "        \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "      } ]" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectation() throws IOException {
        // when
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                        .setHttpRequest(
                                new HttpRequestDTO()
                                        .setPath(string("somePath"))
                        )
                        .setHttpResponse(
                                new HttpResponseDTO()
                                        .setBody(new StringBodyDTO(new StringBody("someBody")))
                        )
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 1," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectationArray() throws IOException {
        // when
        Expectation expectation = new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                )
                .buildObject();
        String jsonExpectation = new ExpectationSerializer().serialize(new Expectation[]{
                expectation,
                expectation,
                expectation
        });

        // then
        assertEquals("[ {" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 1," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}, {" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 1," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}, {" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 1," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"timeToLive\" : {" + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "} ]", jsonExpectation);
    }
}
