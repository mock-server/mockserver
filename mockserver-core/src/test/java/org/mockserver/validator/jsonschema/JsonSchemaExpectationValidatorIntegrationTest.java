package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
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
            "}"), is("11 errors:" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"object instance has properties which are not allowed by the schema: [\"operationId\",\"specUrlOrPayload\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/operationId\" for schema: \"openAPIDefinition/properties/operationId\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"BINARY\"," + NEW_LINE +
            "     \"base64Bytes\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }, " + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"JSON\"," + NEW_LINE +
            "     \"json\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"PARAMETERS\"," + NEW_LINE +
            "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"STRING\"," + NEW_LINE +
            "     \"string\": \"\"" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"XML\"," + NEW_LINE +
            "     \"xml\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/0\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/1\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/2\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/3\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"array\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/4\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/5\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/6\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/7\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
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
            "}"), is("9 errors:" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"body\",\"headers\",\"method\",\"path\",\"socketAddress\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexHeader\": [" + NEW_LINE +
            "           \"^some +regex$\"" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
            "           \"!notThisValue\", " + NEW_LINE +
            "           \"simpleStringMatch\"" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"number\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "           }, " + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"format\": \"ipv4\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpRequest/headers/0/name\" for schema: \"keyToMultiValue/oneOf/0/items/properties/name\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/headers\" for schema: \"keyToMultiValue\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexHeader\": [" + NEW_LINE +
            "           \"^some +regex$\"" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
            "           \"!notThisValue\", " + NEW_LINE +
            "           \"simpleStringMatch\"" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"number\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "           }, " + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"format\": \"ipv4\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpResponse/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/headers/0/values/0\" for schema: \"keyToMultiValue/oneOf/0/items/properties/values/items\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
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
            "}"), is("6 errors:" + NEW_LINE +
            " - field: \"/httpOverrideForwardedRequest/httpRequest/socketAddress/port\" for schema: \"socketAddress/properties/port\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"body\",\"headers\",\"keepAlive\",\"method\",\"path\",\"secure\",\"socketAddress\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/keepAlive\" for schema: \"httpRequest/properties/keepAlive\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"boolean\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/secure\" for schema: \"httpRequest/properties/secure\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"boolean\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/socketAddress/port\" for schema: \"socketAddress/properties/port\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
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
            "}"), is("9 errors:" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"body\",\"cookies\",\"method\",\"path\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/cookies\" for schema: \"keyToValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
            " - field: \"/httpRequest/cookies\" for schema: \"keyToValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/cookies\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexCookie\": \"^some +regex$\", " + NEW_LINE +
            "       \"exampleNottedRegexCookie\": \"!notThisValue\", " + NEW_LINE +
            "       \"exampleSimpleStringCookie\": \"simpleStringMatch\"" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleNumberSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"number\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"examplePatternSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"exampleFormatSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"format\": \"ipv4\"" + NEW_LINE +
            "       }" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpRequest/cookies/0/name\" for schema: \"keyToValue/oneOf/0/items/properties/name\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/cookies\" for schema: \"keyToValue\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexCookie\": \"^some +regex$\", " + NEW_LINE +
            "       \"exampleNottedRegexCookie\": \"!notThisValue\", " + NEW_LINE +
            "       \"exampleSimpleStringCookie\": \"simpleStringMatch\"" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleNumberSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"number\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"examplePatternSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"exampleFormatSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"format\": \"ipv4\"" + NEW_LINE +
            "       }" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpResponse/cookies\" for schema: \"keyToValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/cookies/0/value\" for schema: \"keyToValue/oneOf/0/items/properties/value\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
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
            "}"), is("15 errors:" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"method\",\"path\",\"queryStringParameters\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"keyToMultiValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexParameter\": [" + NEW_LINE +
            "           \"^some +regex$\"" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleNottedAndSimpleStringParameter\": [" + NEW_LINE +
            "           \"!notThisValue\", " + NEW_LINE +
            "           \"simpleStringMatch\"" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleSchemaParameter\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"number\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleMultiSchemaParameter\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "           }, " + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"format\": \"ipv4\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters/0/name\" for schema: \"keyToMultiValue/oneOf/0/items/properties/name\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"BINARY\"," + NEW_LINE +
            "     \"base64Bytes\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }, " + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"JSON\"," + NEW_LINE +
            "     \"json\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"PARAMETERS\"," + NEW_LINE +
            "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"STRING\"," + NEW_LINE +
            "     \"string\": \"\"" + NEW_LINE +
            "   }," + NEW_LINE +
            "   {" + NEW_LINE +
            "     \"type\": \"XML\"," + NEW_LINE +
            "     \"xml\": \"\"," + NEW_LINE +
            "     \"contentType\": \"\"" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/0\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/1\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/2\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/3\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"array\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/4\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/5\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/6\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/body\" for schema: \"bodyWithContentType/anyOf/7\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
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
            "}"), is("13 errors:" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
            " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"headers\",\"method\",\"path\",\"queryStringParameters\"]\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/headers\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexHeader\": [" + NEW_LINE +
            "           \"^some +regex$\"" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
            "           \"!notThisValue\", " + NEW_LINE +
            "           \"simpleStringMatch\"" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"number\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "           }, " + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"format\": \"ipv4\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpRequest/headers/0/values/0\" for schema: \"keyToMultiValue/oneOf/0/items/properties/values/items\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"keyToMultiValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexParameter\": [" + NEW_LINE +
            "           \"^some +regex$\"" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleNottedAndSimpleStringParameter\": [" + NEW_LINE +
            "           \"!notThisValue\", " + NEW_LINE +
            "           \"simpleStringMatch\"" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleSchemaParameter\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"number\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ], " + NEW_LINE +
            "       \"exampleMultiSchemaParameter\": [" + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "           }, " + NEW_LINE +
            "           {" + NEW_LINE +
            "               \"type\": \"string\", " + NEW_LINE +
            "               \"format\": \"ipv4\"" + NEW_LINE +
            "           }" + NEW_LINE +
            "       ]" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpRequest/queryStringParameters/0/name\" for schema: \"keyToMultiValue/oneOf/0/items/properties/name\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/cookies\" for schema: \"keyToValue\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleRegexCookie\": \"^some +regex$\", " + NEW_LINE +
            "       \"exampleNottedRegexCookie\": \"!notThisValue\", " + NEW_LINE +
            "       \"exampleSimpleStringCookie\": \"simpleStringMatch\"" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            "or:" + NEW_LINE +
            NEW_LINE +
            "   {" + NEW_LINE +
            "       \"exampleNumberSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"number\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"examplePatternSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"pattern\": \"^some +regex$\"" + NEW_LINE +
            "       }, " + NEW_LINE +
            "       \"exampleFormatSchemaCookie\": {" + NEW_LINE +
            "           \"type\": \"string\", " + NEW_LINE +
            "           \"format\": \"ipv4\"" + NEW_LINE +
            "       }" + NEW_LINE +
            "   }" + NEW_LINE +
            NEW_LINE +
            " - field: \"/httpResponse/cookies\" for schema: \"keyToValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
            " - field: \"/httpResponse/cookies/0/name\" for schema: \"keyToValue/oneOf/0/items/properties/name\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
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
                    " - oneOf of the following must be specified [\"httpResponse\", \"httpResponseTemplate\", \"httpResponseObjectCallback\", \"httpResponseClassCallback\", \"httpForward\", \"httpForwardTemplate\", \"httpForwardObjectCallback\", \"httpForwardClassCallback\", \"httpOverrideForwardedRequest\", \"httpError\"] but found 0 without errors" + NEW_LINE +
                    " - schema: \"/oneOf/0\" has error: \"object has missing required properties ([\"httpResponse\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/1\" has error: \"object has missing required properties ([\"httpResponseTemplate\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/2\" has error: \"object has missing required properties ([\"httpResponseObjectCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/3\" has error: \"object has missing required properties ([\"httpResponseClassCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/4\" has error: \"object has missing required properties ([\"httpForward\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/5\" has error: \"object has missing required properties ([\"httpForwardTemplate\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/6\" has error: \"object has missing required properties ([\"httpForwardObjectCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/7\" has error: \"object has missing required properties ([\"httpForwardClassCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/8\" has error: \"object has missing required properties ([\"httpOverrideForwardedRequest\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/9\" has error: \"object has missing required properties ([\"httpError\"])\"" + NEW_LINE +
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
                    " - object instance has properties which are not allowed by the schema: [\"invalidField\"]" + NEW_LINE +
                    " - oneOf of the following must be specified [\"httpResponse\", \"httpResponseTemplate\", \"httpResponseObjectCallback\", \"httpResponseClassCallback\", \"httpForward\", \"httpForwardTemplate\", \"httpForwardObjectCallback\", \"httpForwardClassCallback\", \"httpOverrideForwardedRequest\", \"httpError\"] but found 0 without errors" + NEW_LINE +
                    " - schema: \"/oneOf/0\" has error: \"object has missing required properties ([\"httpResponse\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/1\" has error: \"object has missing required properties ([\"httpResponseTemplate\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/2\" has error: \"object has missing required properties ([\"httpResponseObjectCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/3\" has error: \"object has missing required properties ([\"httpResponseClassCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/4\" has error: \"object has missing required properties ([\"httpForward\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/5\" has error: \"object has missing required properties ([\"httpForwardTemplate\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/6\" has error: \"object has missing required properties ([\"httpForwardObjectCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/7\" has error: \"object has missing required properties ([\"httpForwardClassCallback\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/8\" has error: \"object has missing required properties ([\"httpOverrideForwardedRequest\"])\"" + NEW_LINE +
                    " - schema: \"/oneOf/9\" has error: \"object has missing required properties ([\"httpError\"])\"" + NEW_LINE +
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
                "3 errors:" + NEW_LINE +
                    " - field: \"/httpRequest\" for schema: \"httpRequest\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/httpResponse\" for schema: \"httpResponse\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
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
            is("11 errors:" + NEW_LINE +
                " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object has missing required properties ([\"specUrlOrPayload\"])\"" + NEW_LINE +
                " - field: \"/httpRequest\" for schema: \"openAPIDefinition\" has error: \"object instance has properties which are not allowed by the schema: [\"body\",\"cookies\",\"headers\",\"method\",\"path\",\"queryStringParameters\"]\"" + NEW_LINE +
                " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue\" has error: \"instance failed to match exactly one schema (matched 0 out of 2)\"" + NEW_LINE +
                " - field: \"/httpRequest/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/httpRequest/headers\" for schema: \"requestDefinition\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleRegexHeader\": [" + NEW_LINE +
                "           \"^some +regex$\"" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
                "           \"!notThisValue\", " + NEW_LINE +
                "           \"simpleStringMatch\"" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                "or:" + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"number\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                "           }, " + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"format\": \"ipv4\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                " - field: \"/httpRequest/headers/0\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/httpRequest/headers/1\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/httpResponse/headers\" for schema: \"keyToMultiValue\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleRegexHeader\": [" + NEW_LINE +
                "           \"^some +regex$\"" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
                "           \"!notThisValue\", " + NEW_LINE +
                "           \"simpleStringMatch\"" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                "or:" + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"number\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                "           }, " + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"format\": \"ipv4\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                " - field: \"/httpResponse/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/httpResponse/headers/0\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/httpResponse/headers/1\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                NEW_LINE +
                OPEN_API_SPECIFICATION_URL));
    }
}
