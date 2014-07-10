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
                                .setPath("somePath")
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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
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
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
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
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpForward\" : {" + System.getProperty("line.separator") +
                "    \"host\" : \"someHost\"," + System.getProperty("line.separator") +
                "    \"port\" : 1234," + System.getProperty("line.separator") +
                "    \"scheme\" : \"HTTPS\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
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
                "  }" + System.getProperty("line.separator") +
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                "      \"value\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
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
                "  }" + System.getProperty("line.separator") +
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
                "  }" + System.getProperty("line.separator") +
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
                "  }" + System.getProperty("line.separator") +
                "} ]", jsonExpectation);
    }
}
