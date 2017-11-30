package org.mockserver.client.serialization;

import com.google.common.collect.ImmutableList;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Charsets.UTF_8;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerIntegrationTest {

    private final Base64Converter base64Converter = new Base64Converter();
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldAllowSingleObjectForArray() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
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
    public void shouldValidateSingleObjectForArray() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"," + NEW_LINE +
                "        \"extra_field\": \"extra_value\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"," + NEW_LINE +
                "        \"extra_field\": \"extra_value\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "}");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("" +
                "2 errors:" + NEW_LINE +
                " - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpRequest\"" + NEW_LINE +
                " - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpResponse\"");

        // when
        new ExpectationSerializer().deserializeArray(requestBytes);
    }

    @Test
    public void shouldAllowMultipleObjectsForArray() throws IOException {
        // given
        // given
        String requestBytes = ("[" +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "  }," +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "  }," +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"" + NEW_LINE +
                "      }" + NEW_LINE +
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
    public void shouldValidateMultipleObjectsForArray() throws IOException {
        // given
        String requestBytes = ("[" +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "  }," +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "  }," +
                "  {" + NEW_LINE +
                "      \"httpRequest\": {" + NEW_LINE +
                "          \"path\": \"somePath\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\": {" + NEW_LINE +
                "          \"body\": \"someBody\"," + NEW_LINE +
                "          \"extra_field\": \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "  }" +
                "]");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("" +
                "[" + NEW_LINE +
                "  2 errors:" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpRequest\"" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpResponse\"," + NEW_LINE +
                "  2 errors:" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpRequest\"" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpResponse\"," + NEW_LINE +
                "  2 errors:" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpRequest\"" + NEW_LINE +
                "   - object instance has properties which are not allowed by the schema: [\"extra_field\"] for field \"/httpResponse\"" + NEW_LINE +
                "]");

        // when
        new ExpectationSerializer().deserializeArray(requestBytes);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponse() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"someBody\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"connectionOptions\" : {" + NEW_LINE +
                "      \"suppressContentLengthHeader\" : true," + NEW_LINE +
                "      \"contentLengthHeaderOverride\" : 50," + NEW_LINE +
                "      \"suppressConnectionHeader\" : true," + NEW_LINE +
                "      \"keepAliveOverride\" : true," + NEW_LINE +
                "      \"closeSocket\" : true" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(MICROSECONDS)
                                                .setValue(1)
                                )
                                .setConnectionOptions(
                                        new ConnectionOptionsDTO(
                                                new ConnectionOptions()
                                                        .withSuppressContentLengthHeader(true)
                                                        .withContentLengthHeaderOverride(50)
                                                        .withSuppressConnectionHeader(true)
                                                        .withKeepAliveOverride(true)
                                                        .withCloseSocket(true)
                                        )
                                )
                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponseTemplate() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponseTemplate\" : {" + NEW_LINE +
                "    \"templateType\" : \"JAVASCRIPT\"," + NEW_LINE +
                "    \"template\" : \"some_random_template\"," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponseTemplate(
                        new HttpTemplateDTO()
                                .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                                .setTemplate("some_random_template")
                                .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForward() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpForward\" : {" + NEW_LINE +
                "    \"host\" : \"someHost\"," + NEW_LINE +
                "    \"port\" : 1234," + NEW_LINE +
                "    \"scheme\" : \"HTTPS\"" +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
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
    public void shouldDeserializeCompleteObjectWithForwardTemplate() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpForwardTemplate\" : {" + NEW_LINE +
                "    \"templateType\" : \"JAVASCRIPT\"," + NEW_LINE +
                "    \"template\" : \"some_random_template\"," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpForwardTemplate(
                        new HttpTemplateDTO()
                                .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                                .setTemplate("some_random_template")
                                .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithError() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpError\" : {" + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"dropConnection\" : true," + NEW_LINE +
                "    \"responseBytes\" : \"c29tZV9ieXRlcw==\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpError(
                        new HttpErrorDTO()
                                .setDelay(new DelayDTO(new Delay(TimeUnit.HOURS, 1)))
                                .setDropConnection(Boolean.TRUE)
                                .setResponseBytes("some_bytes".getBytes(UTF_8))
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .setTimeToLive(new TimeToLiveDTO(TimeToLive.unlimited())).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithClassCallback() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpClassCallback\" : {" + NEW_LINE +
                "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpClassCallback(
                        new HttpClassCallbackDTO()
                                .setCallbackClass("someClass")
                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithObjectCallback() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"string\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpObjectCallback\" : {" + NEW_LINE +
                "    \"clientId\" : \"someClientId\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpObjectCallback(
                        new HttpObjectCallbackDTO()
                                .setClientId("someClientId")
                )
                .setTimes(new TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = ("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
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
        String requestBytes = ("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"," + NEW_LINE +
                "        \"body\" : {" + NEW_LINE +
                "            \"type\" : \"REGEX\"," + NEW_LINE +
                "            \"regex\" : \"some[a-zA-Z]*\"" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
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
        String requestBytes = ("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"," + NEW_LINE +
                "        \"body\" : {" + NEW_LINE +
                "            \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "            \"parameters\" : [ {" + NEW_LINE +
                "                    \"name\" : \"parameterOneName\"," + NEW_LINE +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + NEW_LINE +
                "                }, {" + NEW_LINE +
                "                    \"name\" : \"parameterTwoName\"," + NEW_LINE +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + NEW_LINE +
                "            } ]" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
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
                                .setHeaders(ImmutableList.of(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(ImmutableList.of(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody(new StringBodyDTO(new StringBody("someBody")))
                                .setHeaders(ImmutableList.of(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(ImmutableList.of(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(MICROSECONDS)
                                                .setValue(1)
                                )
                                .setConnectionOptions(
                                        new ConnectionOptionsDTO(
                                                new ConnectionOptions()
                                                        .withSuppressContentLengthHeader(true)
                                                        .withContentLengthHeaderOverride(50)
                                                        .withSuppressConnectionHeader(true)
                                                        .withKeepAliveOverride(true)
                                                        .withCloseSocket(true)
                                        )
                                )
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"connectionOptions\" : {" + NEW_LINE +
                "      \"suppressContentLengthHeader\" : true," + NEW_LINE +
                "      \"contentLengthHeaderOverride\" : 50," + NEW_LINE +
                "      \"suppressConnectionHeader\" : true," + NEW_LINE +
                "      \"keepAliveOverride\" : true," + NEW_LINE +
                "      \"closeSocket\" : true" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpForward\" : {" + NEW_LINE +
                "    \"host\" : \"someHost\"," + NEW_LINE +
                "    \"port\" : 1234," + NEW_LINE +
                "    \"scheme\" : \"HTTPS\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithError() throws IOException {
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
                                .setHeaders(ImmutableList.of(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(ImmutableList.of(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpError(
                        new HttpErrorDTO()
                                .setResponseBytes("some_bytes".getBytes(UTF_8))
                                .setDelay(new DelayDTO(new Delay(TimeUnit.HOURS, 1)))
                                .setDropConnection(false)
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpError\" : {" + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"dropConnection\" : false," + NEW_LINE +
                "    \"responseBytes\" : \"" + base64Converter.bytesToBase64String("some_bytes".getBytes(UTF_8)) + "\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithClassCallback() throws IOException {
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpClassCallback(
                        new HttpClassCallbackDTO()
                                .setCallbackClass("someClass")
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpClassCallback\" : {" + NEW_LINE +
                "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithObjectCallback() throws IOException {
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpObjectCallback(
                        new HttpObjectCallbackDTO()
                                .setClientId("someClientId")
                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpObjectCallback\" : {" + NEW_LINE +
                "    \"clientId\" : \"someClientId\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithHttpTemplateResponse() throws IOException {
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
                                .setHeaders(Collections.singletonList(new HeaderDTO(new Header("someHeaderName", Collections.singletonList("someHeaderValue")))))
                                .setCookies(Collections.singletonList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                )
                .setHttpResponseTemplate(
                        new HttpTemplateDTO()
                                .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                                .setTemplate("some_random_template")
                                .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

                )
                .setTimes(new TimesDTO(Times.exactly(5)))
                .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2l)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponseTemplate\" : {" + NEW_LINE +
                "    \"template\" : \"some_random_template\"," + NEW_LINE +
                "    \"templateType\" : \"JAVASCRIPT\"," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"XPATH\"," + NEW_LINE +
                "      \"xpath\" : \"/bookstore/book[price>35]/price\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringXmlSchemaBody() throws IOException {
        // when
        String xmlSchema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>";
        String jsonExpectation = new ExpectationSerializer().serialize(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(string("somePath"))
                                .setBody(new XmlSchemaBodyDTO(new XmlSchemaBody(xmlSchema)))
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"XML_SCHEMA\"," + NEW_LINE +
                "      \"xmlSchema\" : \"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringJsonSchemaBody() throws IOException {
        // when
        String jsonSchema = "{" + NEW_LINE +
                "  \"title\": \"Example Schema\"," + NEW_LINE +
                "  \"type\": \"object\"," + NEW_LINE +
                "  \"properties\": {" + NEW_LINE +
                "    \"firstName\": {" + NEW_LINE +
                "      \"type\": \"string\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"lastName\": {" + NEW_LINE +
                "      \"type\": \"string\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"age\": {" + NEW_LINE +
                "      \"description\": \"Age in years\"," + NEW_LINE +
                "      \"type\": \"integer\"," + NEW_LINE +
                "      \"minimum\": 0" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"required\": [\"firstName\", \"lastName\"]" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"JSON_SCHEMA\"," + NEW_LINE +
                "      \"jsonSchema\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"JSON\"," + NEW_LINE +
                "      \"json\" : \"" + StringEscapeUtils.escapeJava(jsonBody) + "\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"" + StringEscapeUtils.escapeJava(jsonBody) + "\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"REGEX\"," + NEW_LINE +
                "      \"regex\" : \"some[a-zA-Z]*\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "      \"parameters\" : [ {" + NEW_LINE +
                "        \"name\" : \"parameterOneName\"," + NEW_LINE +
                "        \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + NEW_LINE +
                "      }, {" + NEW_LINE +
                "        \"name\" : \"parameterTwoName\"," + NEW_LINE +
                "        \"values\" : [ \"parameterTwoValue\" ]" + NEW_LINE +
                "      } ]" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 1," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
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
        assertEquals("[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 1," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 1," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"someBody\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 1," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]", jsonExpectation);
    }
}
