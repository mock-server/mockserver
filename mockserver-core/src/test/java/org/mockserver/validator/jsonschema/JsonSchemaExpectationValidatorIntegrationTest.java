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
            "}"), is("4 errors:" + NEW_LINE +
            " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for schema \"requestDefinition/oneOf/1\" for field \"/httpRequest/operationId\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpResponse/body\" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
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
            " - instance failed to match at least one required schema among 8 for field \"/httpResponse/body\"" + NEW_LINE +
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
            "}"), is("7 errors:" + NEW_LINE +
            " - for field \"/httpRequest/headers\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/headers\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpResponse/headers\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for schema \"keyToMultiValue/oneOf/0\" for field \"/httpResponse/headers/0/values/0\"" + NEW_LINE +
            " - instance type (array) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToMultiValue/oneOf/1\" for field \"/httpResponse/headers\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpResponse/headers\"" + NEW_LINE +
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
            "}"), is("7 errors:" + NEW_LINE +
            " - for field \"/httpRequest/cookies\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleCookieNameOne\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        \"exampleCookieNameTwo\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameOne\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameTwo\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/cookies\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpResponse/cookies\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleCookieNameOne\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        \"exampleCookieNameTwo\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameOne\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameTwo\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for schema \"keyToValue/oneOf/0\" for field \"/httpResponse/cookies/0/value\"" + NEW_LINE +
            " - instance type (array) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToValue/oneOf/1\" for field \"/httpResponse/cookies\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpResponse/cookies\"" + NEW_LINE +
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
            "}"), is("5 errors:" + NEW_LINE +
            " - for field \"/httpRequest/queryStringParameters\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleParameterName\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
            "        \"exampleMultiValuedParameterName\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleParameterName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleMultiValuedParameterName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/queryStringParameters\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpResponse/body\" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
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
            " - instance failed to match at least one required schema among 8 for field \"/httpResponse/body\"" + NEW_LINE +
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
            "}"), is("9 errors:" + NEW_LINE +
            " - for field \"/httpRequest/headers\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - for field \"/httpRequest/queryStringParameters\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleParameterName\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
            "        \"exampleMultiValuedParameterName\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleParameterName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleParameterValue\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleMultiValuedParameterName\"," + NEW_LINE +
            "            \"values\" : [ \"exampleParameterValueOne\", \"exampleParameterValueTwo\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/headers\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/queryStringParameters\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpResponse/cookies\" only one of the following example formats is allowed: " + NEW_LINE +
            NEW_LINE +
            "    {" + NEW_LINE +
            "        \"exampleCookieNameOne\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        \"exampleCookieNameTwo\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "    }" + NEW_LINE +
            NEW_LINE +
            "   or:" + NEW_LINE +
            NEW_LINE +
            "    [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameOne\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueOne\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"name\" : \"exampleCookieNameTwo\"," + NEW_LINE +
            "            \"values\" : \"exampleCookieValueTwo\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for schema \"keyToValue/oneOf/0\" for field \"/httpResponse/cookies/0/name\"" + NEW_LINE +
            " - instance type (array) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToValue/oneOf/1\" for field \"/httpResponse/cookies\"" + NEW_LINE +
            " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpResponse/cookies\"" + NEW_LINE +
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
                "2 errors:" + NEW_LINE +
                    " - oneOf of the following must be specified [\"httpResponse\", \"httpResponseTemplate\", \"httpResponseObjectCallback\", \"httpResponseClassCallback\", \"httpForward\", \"httpForwardTemplate\", \"httpForwardObjectCallback\", \"httpForwardClassCallback\", \"httpOverrideForwardedRequest\", \"httpError\"] but found 0 without errors" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 10)" + NEW_LINE +
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
                "3 errors:" + NEW_LINE +
                    " - object instance has properties which are not allowed by the schema: [\"invalidField\"]" + NEW_LINE +
                    " - oneOf of the following must be specified [\"httpResponse\", \"httpResponseTemplate\", \"httpResponseObjectCallback\", \"httpResponseClassCallback\", \"httpForward\", \"httpForwardTemplate\", \"httpForwardObjectCallback\", \"httpForwardClassCallback\", \"httpOverrideForwardedRequest\", \"httpError\"] but found 0 without errors" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 10)" + NEW_LINE +
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
                "4 errors:" + NEW_LINE +
                    " - instance type (string) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest\"" + NEW_LINE +
                    " - instance type (string) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"requestDefinition/oneOf/1\" for field \"/httpRequest\"" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
                    " - instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"]) for field \"/httpResponse\"" + NEW_LINE +
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
            is(
                "8 errors:" + NEW_LINE +
                    " - for field \"/httpRequest/headers\" only one of the following example formats is allowed: " + NEW_LINE +
                    NEW_LINE +
                    "    {" + NEW_LINE +
                    "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    NEW_LINE +
                    "   or:" + NEW_LINE +
                    NEW_LINE +
                    "    [" + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    ]" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 2) for schema \"requestDefinition/oneOf/0\" for field \"/httpRequest/headers\"" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpRequest\"" + NEW_LINE +
                    " - for field \"/httpResponse/headers\" only one of the following example formats is allowed: " + NEW_LINE +
                    NEW_LINE +
                    "    {" + NEW_LINE +
                    "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "    }" + NEW_LINE +
                    NEW_LINE +
                    "   or:" + NEW_LINE +
                    NEW_LINE +
                    "    [" + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        {" + NEW_LINE +
                    "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
                    "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    ]" + NEW_LINE +
                    " - instance type (string) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToMultiValue/oneOf/0\" for field \"/httpResponse/headers/0\"" + NEW_LINE +
                    " - instance type (string) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToMultiValue/oneOf/0\" for field \"/httpResponse/headers/1\"" + NEW_LINE +
                    " - instance type (array) does not match any allowed primitive type (allowed: [\"object\"]) for schema \"keyToMultiValue/oneOf/1\" for field \"/httpResponse/headers\"" + NEW_LINE +
                    " - instance failed to match exactly one schema (matched 0 out of 2) for field \"/httpResponse/headers\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }
}
