package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator.jsonSchemaExpectationValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidatorIntegrationTest {

    // given
    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaExpectationValidator(new MockServerLogger());

    @Test
    public void shouldValidateSerialisedCompleteDTO() {
        assertThat(jsonSchemaValidator.isValid(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("someMethod"))
                    .setPath(string("somePath"))
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
                        )
                    )
            )
            .setTimes(new TimesDTO(Times.exactly(5))).buildObject().toString()), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpResponse() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "    } ]," + NEW_LINE +
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : 1234," + NEW_LINE +
            "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "    }" + NEW_LINE +
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
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpResponseTemplate() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "    \"template\" : \"return {};\"," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpResponseClassCallback() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  \"httpResponseClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpResponseObjectCallback() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  \"httpResponseObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpForward() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpForwardTemplate() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "    \"template\" : \"return {};\"," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpForwardClassCallback() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  \"httpForwardClassCallback\" : {" + NEW_LINE +
            "    \"callbackClass\" : \"someClass\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpForwardObjectCallback() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  \"httpForwardObjectCallback\" : {" + NEW_LINE +
            "    \"clientId\" : \"someClientId\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpOverrideForwardedRequest() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"httpRequest\" : {" + NEW_LINE +
            "      \"method\" : \"some_overridden_method\"," + NEW_LINE +
            "      \"path\" : \"some_overridden_path\"," + NEW_LINE +
            "      \"body\" : {" + NEW_LINE +
            "        \"type\" : \"STRING\"," + NEW_LINE +
            "        \"string\" : \"some_overridden_body\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"times\" : {" + NEW_LINE +
            "    \"remainingTimes\" : 5," + NEW_LINE +
            "    \"unlimited\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpError() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}"), is(""));
    }

    @Test
    public void shouldValidateValidExpectationOnlyWithHttpResponse() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestBodyAndHttpResponseBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"WRONG\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"WRONG\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInOpenAPIDefinitionAndHttpResponseBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"someMethod\"," + NEW_LINE +
            "    \"operationId\" : 10" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : 50" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("2 errors:" + NEW_LINE +
            " - $.httpRequest.operationId: integer found, string expected" + NEW_LINE +
            " - $.httpResponse.body: should match one of its valid types: {" + NEW_LINE +
            "     \"title\": \"response body\"," + NEW_LINE +
            "     \"anyOf\": [" + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"BINARY\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"base64Bytes\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"JSON\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"json\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": true" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"array\"," + NEW_LINE +
            "         \"additionalProperties\": true" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"PARAMETERS\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"parameters\": {" + NEW_LINE +
            "             \"$ref\": \"#/definitions/keyToMultiValue\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"STRING\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"string\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"string\"" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"XML\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"xml\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }" + NEW_LINE +
            "     ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestHeadersAndHttpResponseHeaders() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : 10," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : 1234," + NEW_LINE +
            "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ 10 ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("5 errors:" + NEW_LINE +
            " - $.httpRequest.headers: array found, object expected" + NEW_LINE +
            " - $.httpRequest.headers[0].name: integer found, string expected" + NEW_LINE +
            " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            " - $.httpResponse.headers: array found, object expected" + NEW_LINE +
            " - $.httpResponse.headers[0].values[0]: integer found, string expected" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpFieldType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"keepAlive\" : \"true\"," + NEW_LINE +
            "    \"secure\" : \"true\"," + NEW_LINE +
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : \"1234\"," + NEW_LINE +
            "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\": {" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "      \"method\": \"POST\", " + NEW_LINE +
            "      \"secure\": false, " + NEW_LINE +
            "      \"keepAlive\": false, " + NEW_LINE +
            "      \"path\": \"/v1.0/publish/pubsub/deathStarStatus\", " + NEW_LINE +
            "      \"pathParameters\": { }, " + NEW_LINE +
            "      \"queryStringParameters\": { }, " + NEW_LINE +
            "      \"cookies\": { }, " + NEW_LINE +
            "      \"headers\": {" + NEW_LINE +
            "        \"Host\": [" + NEW_LINE +
            "          \"localhost:3500\"" + NEW_LINE +
            "        ], " + NEW_LINE +
            "        \"Content-Type\": [" + NEW_LINE +
            "          \"application/json\"" + NEW_LINE +
            "        ]" + NEW_LINE +
            "      }, " + NEW_LINE +
            "      \"body\": {" + NEW_LINE +
            "        \"Content\": {" + NEW_LINE +
            "          \"scenario\": \"MOCK_SERVER\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }, " + NEW_LINE +
            "      \"socketAddress\": {" + NEW_LINE +
            "        \"host\": \"127.0.0.1\", " + NEW_LINE +
            "        \"port\": \"3500\", " + NEW_LINE +
            "        \"scheme\": \"HTTP\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("5 errors:" + NEW_LINE +
            " - $.httpOverrideForwardedRequest.httpRequest.socketAddress.port: string found, integer expected" + NEW_LINE +
            " - $.httpRequest.keepAlive: string found, boolean expected" + NEW_LINE +
            " - $.httpRequest.secure: string found, boolean expected" + NEW_LINE +
            " - $.httpRequest.socketAddress.port: string found, integer expected" + NEW_LINE +
            " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestCookiesAndHttpResponseCookies() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : 10," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : 10" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("5 errors:" + NEW_LINE +
            " - $.httpRequest.cookies: array found, object expected" + NEW_LINE +
            " - $.httpRequest.cookies[0].name: integer found, string expected" + NEW_LINE +
            " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            " - $.httpResponse.cookies: array found, object expected" + NEW_LINE +
            " - $.httpResponse.cookies[0].value: integer found, string expected" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestQueryParametersAndHttpResponseBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : 10," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : 50," + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("4 errors:" + NEW_LINE +
            " - $.httpRequest.queryStringParameters: array found, object expected" + NEW_LINE +
            " - $.httpRequest.queryStringParameters[0].name: integer found, string expected" + NEW_LINE +
            " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            " - $.httpResponse.body: should match one of its valid types: {" + NEW_LINE +
            "     \"title\": \"response body\"," + NEW_LINE +
            "     \"anyOf\": [" + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"BINARY\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"base64Bytes\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"JSON\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"json\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": true" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"array\"," + NEW_LINE +
            "         \"additionalProperties\": true" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"PARAMETERS\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"parameters\": {" + NEW_LINE +
            "             \"$ref\": \"#/definitions/keyToMultiValue\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"STRING\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"string\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"string\"" + NEW_LINE +
            "       }," + NEW_LINE +
            "       {" + NEW_LINE +
            "         \"type\": \"object\"," + NEW_LINE +
            "         \"additionalProperties\": false," + NEW_LINE +
            "         \"properties\": {" + NEW_LINE +
            "           \"not\": {" + NEW_LINE +
            "             \"type\": \"boolean\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"type\": {" + NEW_LINE +
            "             \"enum\": [" + NEW_LINE +
            "               \"XML\"" + NEW_LINE +
            "             ]" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"xml\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }," + NEW_LINE +
            "           \"contentType\": {" + NEW_LINE +
            "             \"type\": \"string\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "         }" + NEW_LINE +
            "       }" + NEW_LINE +
            "     ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestQueryParametersAndHeaderAndHttpResponseCookies() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : 10," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ 10 ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : 10," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is("7 errors:" + NEW_LINE +
            " - $.httpRequest.headers: array found, object expected" + NEW_LINE +
            " - $.httpRequest.headers[0].values[0]: integer found, string expected" + NEW_LINE +
            " - $.httpRequest.queryStringParameters: array found, object expected" + NEW_LINE +
            " - $.httpRequest.queryStringParameters[0].name: integer found, string expected" + NEW_LINE +
            " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            " - $.httpResponse.cookies: array found, object expected" + NEW_LINE +
            " - $.httpResponse.cookies[0].name: integer found, string expected" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldValidateInvalidExpectationMissingAction() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"),
            is(
                "11 errors:" + NEW_LINE +
                    " - $.httpError: is missing, but is required, if specifying action of type Error" + NEW_LINE +
                    " - $.httpForward: is missing, but is required, if specifying action of type Forward" + NEW_LINE +
                    " - $.httpForwardClassCallback: is missing, but is required, if specifying action of type ForwardClassCallback" + NEW_LINE +
                    " - $.httpForwardObjectCallback: is missing, but is required, if specifying action of type ForwardObjectCallback" + NEW_LINE +
                    " - $.httpForwardTemplate: is missing, but is required, if specifying action of type ForwardTemplate" + NEW_LINE +
                    " - $.httpOverrideForwardedRequest: is missing, but is required, if specifying action of type OverrideForwardedRequest" + NEW_LINE +
                    " - $.httpResponse: is missing, but is required, if specifying action of type Response" + NEW_LINE +
                    " - $.httpResponseClassCallback: is missing, but is required, if specifying action of type ResponseClassCallback" + NEW_LINE +
                    " - $.httpResponseObjectCallback: is missing, but is required, if specifying action of type ResponseObjectCallback" + NEW_LINE +
                    " - $.httpResponseTemplate: is missing, but is required, if specifying action of type ResponseTemplate" + NEW_LINE +
                    " - oneOf of the following must be specified [httpError, httpForward, httpForwardClassCallback, httpForwardObjectCallback, httpForwardTemplate, httpOverrideForwardedRequest, httpResponse, httpResponseClassCallback, httpResponseObjectCallback, httpResponseTemplate]" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidExtraField() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"invalidField\" : \"randomValue\"" + NEW_LINE +
                "  }"),
            is(
                "12 errors:" + NEW_LINE +
                    " - $.httpError: is missing, but is required, if specifying action of type Error" + NEW_LINE +
                    " - $.httpForward: is missing, but is required, if specifying action of type Forward" + NEW_LINE +
                    " - $.httpForwardClassCallback: is missing, but is required, if specifying action of type ForwardClassCallback" + NEW_LINE +
                    " - $.httpForwardObjectCallback: is missing, but is required, if specifying action of type ForwardObjectCallback" + NEW_LINE +
                    " - $.httpForwardTemplate: is missing, but is required, if specifying action of type ForwardTemplate" + NEW_LINE +
                    " - $.httpOverrideForwardedRequest: is missing, but is required, if specifying action of type OverrideForwardedRequest" + NEW_LINE +
                    " - $.httpResponse: is missing, but is required, if specifying action of type Response" + NEW_LINE +
                    " - $.httpResponseClassCallback: is missing, but is required, if specifying action of type ResponseClassCallback" + NEW_LINE +
                    " - $.httpResponseObjectCallback: is missing, but is required, if specifying action of type ResponseObjectCallback" + NEW_LINE +
                    " - $.httpResponseTemplate: is missing, but is required, if specifying action of type ResponseTemplate" + NEW_LINE +
                    " - $.invalidField: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
                    " - oneOf of the following must be specified [httpError, httpForward, httpForwardClassCallback, httpForwardObjectCallback, httpForwardTemplate, httpOverrideForwardedRequest, httpResponse, httpResponseClassCallback, httpResponseObjectCallback, httpResponseTemplate]" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateMultipleInvalidFieldTypes() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"httpRequest\" : \"100\"," + NEW_LINE +
                "    \"httpResponse\" : false" + NEW_LINE +
                "  }"),
            is(
                "2 errors:" + NEW_LINE +
                    " - $.httpRequest: string found, object expected" + NEW_LINE +
                    " - $.httpResponse: boolean found, object expected" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
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
                "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"someBody\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]," + NEW_LINE +
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
                "}"),
            is("7 errors:" + NEW_LINE +
                " - $.httpRequest.headers: array found, object expected" + NEW_LINE +
                " - $.httpRequest.headers[0]: string found, object expected" + NEW_LINE +
                " - $.httpRequest.headers[1]: string found, object expected" + NEW_LINE +
                " - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
                " - $.httpResponse.headers: array found, object expected" + NEW_LINE +
                " - $.httpResponse.headers[0]: string found, object expected" + NEW_LINE +
                " - $.httpResponse.headers[1]: string found, object expected" + NEW_LINE +
                NEW_LINE +
                OPEN_API_SPECIFICATION_URL));
    }
}
