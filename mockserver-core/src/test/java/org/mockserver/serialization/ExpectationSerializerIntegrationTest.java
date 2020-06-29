package org.mockserver.serialization;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializerIntegrationTest {

    @Test
    public void shouldAllowSingleObjectForArray() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "    \"id\" : \"some_key\"," + NEW_LINE +
            "    \"priority\" : 10," + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": \"somePath\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\": \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        Expectation[] expectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(requestBytes, false);

        // then
        assertArrayEquals(new Expectation[]{
            new ExpectationDTO()
                .setId("some_key")
                .setPriority(10)
                .setHttpRequest(
                    new HttpRequestDTO()
                        .setPath(string("somePath"))
                )
                .setHttpResponse(
                    new HttpResponseDTO()
                        .setBody(new StringBodyDTO(exact("someBody")))
                )
                .buildObject()
        }, expectations);
    }

    @Test
    public void shouldValidateSingleObjectForArray() {
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

        // when
        try {
            new ExpectationSerializer(new MockServerLogger()).deserializeArray(requestBytes, false);
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            assertThat(throwable, instanceOf(IllegalArgumentException.class));
            assertThat(throwable.getMessage(), is("incorrect expectation json format for:" + NEW_LINE +
                "" + NEW_LINE +
                "  {" + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"path\" : \"somePath\"," + NEW_LINE +
                "      \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\" : {" + NEW_LINE +
                "      \"body\" : \"someBody\"," + NEW_LINE +
                "      \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }" + NEW_LINE +
                "" + NEW_LINE +
                " schema validation errors:" + NEW_LINE +
                "" + NEW_LINE +
                "  4 errors:" + NEW_LINE +
                "   - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "   - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
                "   - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\",\"path\"]\"" + NEW_LINE +
                "   - field: \"/httpResponse\" for schema: \"httpResponse\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "  " + NEW_LINE +
                "  " + OPEN_API_SPECIFICATION_URL));
        }
    }

    @Test
    public void shouldAllowMultipleObjectsForArray() {
        // given
        // given
        String requestBytes = ("[" +
            "  {" + NEW_LINE +
            "      \"id\" : \"some_key\"," + NEW_LINE +
            "      \"priority\" : 10," + NEW_LINE +
            "      \"httpRequest\": {" + NEW_LINE +
            "          \"path\": \"somePath\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\": {" + NEW_LINE +
            "          \"body\": \"someBody\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "  }," +
            "  {" + NEW_LINE +
            "      \"id\" : \"some_key\"," + NEW_LINE +
            "      \"priority\" : 10," + NEW_LINE +
            "      \"httpRequest\": {" + NEW_LINE +
            "          \"path\": \"somePath\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\": {" + NEW_LINE +
            "          \"body\": \"someBody\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "  }," +
            "  {" + NEW_LINE +
            "      \"id\" : \"some_key\"," + NEW_LINE +
            "      \"priority\" : 10," + NEW_LINE +
            "      \"httpRequest\": {" + NEW_LINE +
            "          \"path\": \"somePath\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"httpResponse\": {" + NEW_LINE +
            "          \"body\": \"someBody\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "  }" +
            "]");
        Expectation expectation = new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject();

        // when
        Expectation[] expectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(requestBytes, false);

        // then
        assertArrayEquals(new Expectation[]{
            expectation,
            expectation,
            expectation
        }, expectations);
    }

    @Test
    public void shouldValidateMultipleObjectsForArray() {
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

        // when
        try {
            new ExpectationSerializer(new MockServerLogger()).deserializeArray(requestBytes, false);
            fail("expected exception to be thrown");
        } catch (Throwable throwable) {
            assertThat(throwable, instanceOf(IllegalArgumentException.class));
            assertThat(throwable.getMessage(), is("[" + NEW_LINE +
                "  incorrect expectation json format for:" + NEW_LINE +
                "  " + NEW_LINE +
                "    {" + NEW_LINE +
                "      \"httpRequest\" : {" + NEW_LINE +
                "        \"path\" : \"somePath\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\" : {" + NEW_LINE +
                "        \"body\" : \"someBody\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "    }" + NEW_LINE +
                "  " + NEW_LINE +
                "   schema validation errors:" + NEW_LINE +
                "  " + NEW_LINE +
                "    4 errors:" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\",\"path\"]\"" + NEW_LINE +
                "     - field: \"/httpResponse\" for schema: \"httpResponse\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "    " + NEW_LINE +
                "    " + OPEN_API_SPECIFICATION_URL + "," + NEW_LINE +
                "  " + NEW_LINE +
                "  incorrect expectation json format for:" + NEW_LINE +
                "  " + NEW_LINE +
                "    {" + NEW_LINE +
                "      \"httpRequest\" : {" + NEW_LINE +
                "        \"path\" : \"somePath\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\" : {" + NEW_LINE +
                "        \"body\" : \"someBody\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "    }" + NEW_LINE +
                "  " + NEW_LINE +
                "   schema validation errors:" + NEW_LINE +
                "  " + NEW_LINE +
                "    4 errors:" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\",\"path\"]\"" + NEW_LINE +
                "     - field: \"/httpResponse\" for schema: \"httpResponse\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "    " + NEW_LINE +
                "    " + OPEN_API_SPECIFICATION_URL + "," + NEW_LINE +
                "  " + NEW_LINE +
                "  incorrect expectation json format for:" + NEW_LINE +
                "  " + NEW_LINE +
                "    {" + NEW_LINE +
                "      \"httpRequest\" : {" + NEW_LINE +
                "        \"path\" : \"somePath\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"httpResponse\" : {" + NEW_LINE +
                "        \"body\" : \"someBody\"," + NEW_LINE +
                "        \"extra_field\" : \"extra_value\"" + NEW_LINE +
                "      }" + NEW_LINE +
                "    }" + NEW_LINE +
                "  " + NEW_LINE +
                "   schema validation errors:" + NEW_LINE +
                "  " + NEW_LINE +
                "    4 errors:" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
                "     - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\",\"path\"]\"" + NEW_LINE +
                "     - field: \"/httpResponse\" for schema: \"httpResponse\" has error: \"object instance has properties which are not allowed by the schema: [\"extra_field\"]\"" + NEW_LINE +
                "    " + NEW_LINE +
                "    " + OPEN_API_SPECIFICATION_URL + NEW_LINE +
                "]"));
        }
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponse() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : 1234," + NEW_LINE +
            "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "    }," +
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
            "      \"closeSocket\" : true," + NEW_LINE +
            "      \"closeSocketDelay\" : {" + NEW_LINE +
            "        \"timeUnit\" : \"MILLISECONDS\"," + NEW_LINE +
            "        \"value\" : 100" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
                    .setSocketAddress(new SocketAddressDTO(
                        new SocketAddress()
                            .withHost("someHost")
                            .withPort(1234)
                            .withScheme(SocketAddress.Scheme.HTTPS)
                    ))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setStatusCode(304)
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
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
                                .withCloseSocketDelay(new Delay(MILLISECONDS, 100))
                        )
                    )
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObjectFromToString() {
        // given
        Expectation expected = new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
                    .setSocketAddress(new SocketAddressDTO(
                        new SocketAddress()
                            .withHost("someHost")
                            .withPort(1234)
                            .withScheme(SocketAddress.Scheme.HTTPS)
                    ))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setStatusCode(304)
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
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
                                .withCloseSocketDelay(new Delay(MILLISECONDS, 100))
                        )
                    )
            )
            .setTimes(new TimesDTO(Times.exactly(5))).buildObject();
        String requestBytes = expected.toString();

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then;
        assertEquals(expected, expectation);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithParameterRequestBody() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"POST\"," + NEW_LINE +
            "    \"path\" : \"some_pathRequest\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "      \"parameters\" : {" + NEW_LINE +
            "        \"bodyParameterOneName\" : [ \"Parameter One Value One\", \"Parameter One Value Two\" ]," + NEW_LINE +
            "        \"bodyParameterTwoName\" : [ \"Parameter Two\" ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 202," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"headerNameResponse\" : [ \"headerValueResponse\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"cookieNameResponse\" : \"cookieValueResponse\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"some_body_response\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("POST"))
                    .setPath(string("some_pathRequest"))
                    .setBody(new ParameterBodyDTO(params(
                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                        param("bodyParameterTwoName", "Parameter Two")
                    )))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setStatusCode(202)
                    .setBody(new StringBodyDTO(exact("some_body_response")))
                    .setHeaders(new Headers().withEntries(
                        header("headerNameResponse", "headerValueResponse")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("cookieNameResponse", "cookieValueResponse")
                    ))
            ).buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponseTemplate() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseTemplate(
                new HttpTemplateDTO()
                    .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                    .setTemplate("some_random_template")
                    .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponseClassCallback() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
            "  \"httpResponseClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseClassCallback(
                new HttpClassCallbackDTO()
                    .setCallbackClass("someClass")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithResponseObjectCallback() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
            "  \"httpResponseObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseObjectCallback(
                new HttpObjectCallbackDTO()
                    .setClientId("someClientId")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForward() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForward(
                new HttpForwardDTO()
                    .setHost("someHost")
                    .setPort(1234)
                    .setScheme(HttpForward.Scheme.HTTPS)
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.unlimited())).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForwardTemplate() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardTemplate(
                new HttpTemplateDTO()
                    .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                    .setTemplate("some_random_template")
                    .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForwardClassCallback() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
            "  \"httpForwardClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardClassCallback(
                new HttpClassCallbackDTO()
                    .setCallbackClass("someClass")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithForwardObjectCallback() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
            "  \"httpForwardObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardObjectCallback(
                new HttpObjectCallbackDTO()
                    .setClientId("someClientId")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithOverrideForwardedRequest() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"httpRequest\" : {" + NEW_LINE +
            "      \"method\" : \"some_overridden_method\"," + NEW_LINE +
            "      \"path\" : \"some_overridden_path\"," + NEW_LINE +
            "      \"body\" : \"some_overridden_body\"" + NEW_LINE +
            "    }," + NEW_LINE +
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
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpOverrideForwardedRequest(
                new HttpOverrideForwardedRequestDTO()
                    .setHttpRequest(
                        new HttpRequestDTO()
                            .setMethod(string("some_overridden_method"))
                            .setPath(string("some_overridden_path"))
                            .setBody(new StringBodyDTO(exact("some_overridden_body")))
                    )
                    .setDelay(new DelayDTO()
                        .setTimeUnit(MICROSECONDS)
                        .setValue(1)
                    )
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializeCompleteObjectWithError() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"pathParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpError(
                new HttpErrorDTO()
                    .setDelay(new DelayDTO(new Delay(TimeUnit.HOURS, 1)))
                    .setDropConnection(Boolean.TRUE)
                    .setResponseBytes("some_bytes".getBytes(UTF_8))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.unlimited())).buildObject(), expectation
        );
    }

    @Test
    public void shouldDeserializePartialObject() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "    \"id\" : \"some_key\"," + NEW_LINE +
            "    \"priority\" : 10," + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": \"somePath\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\": \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeStringRegexBody() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "    \"id\" : \"some_key\"," + NEW_LINE +
            "    \"priority\" : 10," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeParameterBody() {
        // given
        String requestBytes = ("{" + NEW_LINE +
            "    \"id\" : \"some_key\"," + NEW_LINE +
            "    \"priority\" : 10," + NEW_LINE +
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
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
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
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponse() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setStatusCode(304)
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
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
                                .withCloseSocketDelay(new Delay(MILLISECONDS, 100))
                        )
                    )
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
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
            "      \"closeSocket\" : true," + NEW_LINE +
            "      \"closeSocketDelay\" : {" + NEW_LINE +
            "        \"timeUnit\" : \"MILLISECONDS\"," + NEW_LINE +
            "        \"value\" : 100" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponseTemplate() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseTemplate(
                new HttpTemplateDTO()
                    .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                    .setTemplate("some_random_template")
                    .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
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
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponseClassCallback() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseClassCallback(
                new HttpClassCallbackDTO()
                    .setCallbackClass("someClass")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponseClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithResponseObjectCallback() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseObjectCallback(
                new HttpObjectCallbackDTO()
                    .setClientId("someClientId")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponseObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithForward() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForward(
                new HttpForwardDTO()
                    .setHost("someHost")
                    .setPort(1234)
                    .setScheme(HttpForward.Scheme.HTTPS)
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpForward\" : {" + NEW_LINE +
            "    \"host\" : \"someHost\"," + NEW_LINE +
            "    \"port\" : 1234," + NEW_LINE +
            "    \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithForwardTemplate() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardTemplate(
                new HttpTemplateDTO()
                    .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                    .setTemplate("some_random_template")
                    .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpForwardTemplate\" : {" + NEW_LINE +
            "    \"template\" : \"some_random_template\"," + NEW_LINE +
            "    \"templateType\" : \"JAVASCRIPT\"," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithForwardClassCallback() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardClassCallback(
                new HttpClassCallbackDTO()
                    .setCallbackClass("someClass")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpForwardClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithForwardObjectCallback() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpForwardObjectCallback(
                new HttpObjectCallbackDTO()
                    .setClientId("someClientId")
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpForwardObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithOverrideForwardedRequest() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpOverrideForwardedRequest(
                new HttpOverrideForwardedRequestDTO()
                    .setHttpRequest(
                        new HttpRequestDTO()
                            .setMethod(string("some_overridden_method"))
                            .setPath(string("some_overridden_path"))
                            .setBody(new StringBodyDTO(exact("some_overridden_body")))
                    )
                    .setDelay(new DelayDTO()
                        .setTimeUnit(MICROSECONDS)
                        .setValue(1)
                    )
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5))).buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"httpRequest\" : {" + NEW_LINE +
            "      \"method\" : \"some_overridden_method\"," + NEW_LINE +
            "      \"path\" : \"some_overridden_path\"," + NEW_LINE +
            "      \"body\" : \"some_overridden_body\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithError() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpError(
                new HttpErrorDTO()
                    .setResponseBytes("some_bytes".getBytes(UTF_8))
                    .setDelay(new DelayDTO(new Delay(TimeUnit.HOURS, 1)))
                    .setDropConnection(false)
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpError\" : {" + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"dropConnection\" : false," + NEW_LINE +
            "    \"responseBytes\" : \"c29tZV9ieXRlcw==\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeCompleteObjectWithHttpTemplateResponse() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
                    .setQueryStringParameters(new Parameters().withEntries(
                        param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    ))
                    .setBody(new StringBodyDTO(exact("someBody")))
                    .setHeaders(new Headers().withEntries(
                        header("someHeaderName", "someHeaderValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("someCookieName", "someCookieValue")
                    ))
            )
            .setHttpResponseTemplate(
                new HttpTemplateDTO()
                    .setTemplateType(HttpTemplate.TemplateType.JAVASCRIPT)
                    .setTemplate("some_random_template")
                    .setDelay(new DelayDTO(new Delay(MICROSECONDS, 1)))

            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"pathParameters\" : {" + NEW_LINE +
            "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"queryStringParameters\" : {" + NEW_LINE +
            "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
            "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"headers\" : {" + NEW_LINE +
            "      \"someHeaderName\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : {" + NEW_LINE +
            "      \"someCookieName\" : \"someCookieValue\"" + NEW_LINE +
            "    }," + NEW_LINE +
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
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialRequestAndResponse() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringXPathBody() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new XPathBodyDTO(new XPathBody("/bookstore/book[price>35]/price")))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
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
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringXmlSchemaBody() {
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
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new XmlSchemaBodyDTO(new XmlSchemaBody(xmlSchema)))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
                "  \"id\" : \"some_key\"," + NEW_LINE +
                "  \"priority\" : 10," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"XML_SCHEMA\"," + NEW_LINE +
                "      \"xmlSchema\" : \"" + StringEscapeUtils.escapeJava(xmlSchema) + "\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"JSON\"," + NEW_LINE +
                "      \"json\" : {" + NEW_LINE +
                "        \"fieldOne\" : \"valueOne\"," + NEW_LINE +
                "        \"fieldTwo\" : \"valueTwo\"" + NEW_LINE +
                "      }," + NEW_LINE +
                "      \"rawBytes\" : \"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"timeToLive\" : 2" + NEW_LINE +
                "  }" + NEW_LINE +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringJsonSchemaBody() {
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
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema)))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.HOURS, 2L)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON_SCHEMA\"," + NEW_LINE +
            "      \"jsonSchema\" : {" + NEW_LINE +
            "        \"title\" : \"Example Schema\"," + NEW_LINE +
            "        \"type\" : \"object\"," + NEW_LINE +
            "        \"properties\" : {" + NEW_LINE +
            "          \"firstName\" : {" + NEW_LINE +
            "            \"type\" : \"string\"" + NEW_LINE +
            "          }," + NEW_LINE +
            "          \"lastName\" : {" + NEW_LINE +
            "            \"type\" : \"string\"" + NEW_LINE +
            "          }," + NEW_LINE +
            "          \"age\" : {" + NEW_LINE +
            "            \"description\" : \"Age in years\"," + NEW_LINE +
            "            \"type\" : \"integer\"," + NEW_LINE +
            "            \"minimum\" : 0" + NEW_LINE +
            "          }" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"required\" : [ \"firstName\", \"lastName\" ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"fieldOne\" : \"valueOne\"," + NEW_LINE +
            "        \"fieldTwo\" : \"valueTwo\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"rawBytes\" : \"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
            "    \"timeToLive\" : 2" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringJsonBody() {
        // when
        String jsonBody = "{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}";
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new JsonBodyDTO(new JsonBody(jsonBody)))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody(jsonBody)))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"fieldOne\" : \"valueOne\"," + NEW_LINE +
            "        \"fieldTwo\" : \"valueTwo\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"rawBytes\" : \"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"fieldOne\" : \"valueOne\"," + NEW_LINE +
            "        \"fieldTwo\" : \"valueTwo\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"rawBytes\" : \"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringRegexBody() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
                    .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
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
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializeStringParameterBody() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
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
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)))
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "      \"parameters\" : {" + NEW_LINE +
            "        \"parameterOneName\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]," + NEW_LINE +
            "        \"parameterTwoName\" : [ \"parameterTwoValue\" ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectation() {
        // when
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonExpectation);
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldSerializePartialExpectationArray() {
        // when
        Expectation expectation = new ExpectationDTO()
            .setId("some_key")
            .setPriority(10)
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("somePath"))
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(exact("someBody")))
            )
            .buildObject();
        String jsonExpectation = new ExpectationSerializer(new MockServerLogger()).serialize(new Expectation[]{
            expectation,
            expectation,
            expectation
        });

        // then
        assertEquals("[ {" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"id\" : \"some_key\"," + NEW_LINE +
            "  \"priority\" : 10," + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"someBody\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"timeToLive\" : {" + NEW_LINE +
            "    \"unlimited\" : true" + NEW_LINE +
            "  }" + NEW_LINE +
            "} ]", jsonExpectation);
    }
}
