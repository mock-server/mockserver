package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator.jsonSchemaExpectationValidator;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidatorTest {

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
                    .setSocketAddress(new SocketAddress()
                        .withHost("someHost")
                        .withPort(1234)
                        .withScheme(SocketAddress.Scheme.HTTPS)
                    )
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
    public void shouldValidateExpectationWithCaptureRules() {
        // when — a capture block (WS2.2) validates against the schema
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/login\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"ok\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"capture\" : [ {" + NEW_LINE +
            "    \"source\" : \"jsonPath\"," + NEW_LINE +
            "    \"expression\" : \"$.userId\"," + NEW_LINE +
            "    \"into\" : \"user\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldRejectCaptureRuleWithUnknownSource() {
        // when — an unknown capture source is rejected by the schema
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : \"ok\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"capture\" : [ {" + NEW_LINE +
            "    \"source\" : \"notARealSource\"," + NEW_LINE +
            "    \"expression\" : \"$.userId\"," + NEW_LINE +
            "    \"into\" : \"user\"" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}"), is(not("")));
    }

    @Test
    public void shouldAcceptEveryThreeDigitResponseStatusCode() {
        for (int statusCode : new int[]{100, 200, 599, 999}) {
            assertThat(String.valueOf(statusCode), jsonSchemaValidator.isValid(
                "{ \"httpRequest\" : { \"path\" : \"/s\" }, \"httpResponse\" : { \"statusCode\" : " + statusCode + " } }"), is(""));
        }
    }

    @Test
    public void shouldRejectAResponseStatusCodeOutsideOneHundredToNineNineNine() {
        // an HTTP status line carries three digits: 0 or 1000 was registered and then served as "HTTP/1.1 0"
        for (int statusCode : new int[]{-5, 0, 99, 1000}) {
            String result = jsonSchemaValidator.isValid(
                "{ \"httpRequest\" : { \"path\" : \"/s\" }, \"httpResponse\" : { \"statusCode\" : " + statusCode + " } }");
            assertThat(String.valueOf(statusCode), result, containsString("$.httpResponse.statusCode: must have a " + (statusCode < 100 ? "minimum value of 100" : "maximum value of 999")));
        }
    }

    @Test
    public void shouldRejectAnOutOfRangeStatusCodeInAFallbackSequenceOrSseResponse() {
        assertThat(jsonSchemaValidator.isValid("{ \"httpRequest\" : { \"path\" : \"/s\" }, \"httpForwardWithFallback\" : { \"httpForward\" : { \"host\" : \"localhost\", \"port\" : 1 }, \"fallbackResponse\" : { \"statusCode\" : 0 } } }"),
            containsString("$.httpForwardWithFallback.fallbackResponse.statusCode: must have a minimum value of 100"));
        assertThat(jsonSchemaValidator.isValid("{ \"httpRequest\" : { \"path\" : \"/s\" }, \"httpResponses\" : [ { \"statusCode\" : 200 }, { \"statusCode\" : 1000 } ] }"),
            containsString("$.httpResponses[1].statusCode: must have a maximum value of 999"));
        assertThat(jsonSchemaValidator.isValid("{ \"httpRequest\" : { \"path\" : \"/s\" }, \"httpSseResponse\" : { \"statusCode\" : 0, \"events\" : [ { \"data\" : \"x\" } ] } }"),
            containsString("$.httpSseResponse.statusCode: must have a minimum value of 100"));
    }

    @Test
    public void shouldValidateExpectationWithGenerateFromSchemaResponse() {
        // when — an httpResponse carrying an inline JSON schema is accepted by the schema
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/users\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 200," + NEW_LINE +
            "    \"generateFromSchema\" : \"{\\\"type\\\":\\\"object\\\"}\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
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
    public void shouldValidateExpectationWithResponseModifierJsonPatchAndMergePatch() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"jsonPatch\" : [ {" + NEW_LINE +
            "        \"op\" : \"replace\"," + NEW_LINE +
            "        \"path\" : \"/name\"," + NEW_LINE +
            "        \"value\" : \"new\"" + NEW_LINE +
            "      } ]," + NEW_LINE +
            "      \"jsonMergePatch\" : {" + NEW_LINE +
            "        \"removed\" : null" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldRejectResponseModifierWithJsonPatchOfWrongType() {
        // jsonPatch must be an array; an object is invalid
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"jsonPatch\" : {" + NEW_LINE +
            "        \"op\" : \"replace\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(not("")));
    }

    @Test
    public void shouldValidateValidCompleteExpectationWithHttpForwardValidateAction() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpForwardValidateAction\" : {" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"org/mockserver/openapi/openapi_petstore_example.json\"," + NEW_LINE +
            "    \"host\" : \"localhost\"," + NEW_LINE +
            "    \"port\" : 8080," + NEW_LINE +
            "    \"scheme\" : \"HTTPS\"," + NEW_LINE +
            "    \"validateRequest\" : true," + NEW_LINE +
            "    \"validateResponse\" : true," + NEW_LINE +
            "    \"validationMode\" : \"STRICT\"" + NEW_LINE +
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
        // when - "WRONG" type is accepted by additionalProperties=true object matcher in body anyOf
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
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"someMethod\"," + NEW_LINE +
            "    \"operationId\" : 10" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : 50" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // then
        assertThat(result, startsWith("7 error"));
        assertThat(result, containsString("$.httpRequest.anyOf: is missing"));
        assertThat(result, containsString("$.httpRequest.operationId: integer found, string expected"));
        assertThat(result, containsString("$.httpRequest.binaryData: is missing, but is required"));
        assertThat(result, containsString("$.httpRequest.dnsName: is missing, but is required"));
        assertThat(result, containsString("$.httpResponse.body: should match one of its valid types"));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestHeadersAndHttpResponseHeaders() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("14 error"));
        assertThat(result, containsString("$.httpRequest.headers[0].name: integer found, string expected"));
        assertThat(result, containsString("$.httpRequest.headers: array found, object expected"));
        assertThat(result, containsString("$.httpResponse.headers[0].values[0]: integer found, string expected"));
        assertThat(result, containsString("$.httpResponse.headers: array found, object expected"));
        // The array branch's `name` and `values` are now `stringOrJsonSchema` (a oneOf) rather than
        // a bare string, so that they can carry the object form for a name/value whose first
        // character is a marker. An invalid entry therefore reports the failing branch AND the
        // enclosing oneOf — two lines instead of one, which is what raised this count from 11.
        // The map branch has always reported this way; the array branch now matches it.
        assertThat(result, containsString("$.httpRequest.headers[0].name: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpResponse.headers[0].values[0]: should be valid to one and only one schema, but 0 are valid"));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpFieldType() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("12 error"));
        assertThat(result, containsString("$.httpRequest.keepAlive: string found, boolean expected"));
        assertThat(result, containsString("$.httpRequest.secure: string found, boolean expected"));
        assertThat(result, containsString("$.httpRequest.socketAddress.port: string found, integer expected"));
        assertThat(result, containsString("$.httpOverrideForwardedRequest.httpRequest.socketAddress.port: string found, integer expected"));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestCookiesAndHttpResponseCookies() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("14 error"));
        assertThat(result, containsString("$.httpRequest.cookies[0].name: integer found, string expected"));
        assertThat(result, containsString("$.httpRequest.cookies: array found, object expected"));
        assertThat(result, containsString("$.httpResponse.cookies[0].value: integer found, string expected"));
        assertThat(result, containsString("$.httpResponse.cookies: array found, object expected"));
        // the array branch now types name/value as stringOrJsonSchema (a oneOf), so an invalid
        // entry reports the failing branch AND the enclosing oneOf — these are the lines that
        // raised the count above, asserted explicitly so an unintended extra error cannot hide
        // inside the number
        assertThat(result, containsString("$.httpRequest.cookies[0].name: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpResponse.cookies[0].value: should be valid to one and only one schema, but 0 are valid"));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestQueryParametersAndHttpResponseBody() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("11 error"));
        assertThat(result, containsString("$.httpRequest.queryStringParameters[0].name: integer found, string expected"));
        assertThat(result, containsString("$.httpRequest.queryStringParameters: array found, object expected"));
        assertThat(result, containsString("$.httpResponse.body: should match one of its valid types"));
        // the array branch now types name/value as stringOrJsonSchema (a oneOf), so an invalid
        // entry reports the failing branch AND the enclosing oneOf — these are the lines that
        // raised the count above, asserted explicitly so an unintended extra error cannot hide
        // inside the number
        assertThat(result, containsString("$.httpRequest.queryStringParameters[0].name: should be valid to one and only one schema, but 0 are valid"));
    }

    @Test
    public void shouldValidateInvalidExpectationWithErrorsInHttpRequestQueryParametersAndHeaderAndHttpResponseCookies() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("18 error"));
        assertThat(result, containsString("$.httpRequest.queryStringParameters[0].name: integer found, string expected"));
        assertThat(result, containsString("$.httpRequest.headers[0].values[0]: integer found, string expected"));
        assertThat(result, containsString("$.httpResponse.cookies[0].name: integer found, string expected"));
        // the array branch now types name/value as stringOrJsonSchema (a oneOf), so an invalid
        // entry reports the failing branch AND the enclosing oneOf — these are the lines that
        // raised the count above, asserted explicitly so an unintended extra error cannot hide
        // inside the number
        assertThat(result, containsString("$.httpRequest.queryStringParameters[0].name: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpRequest.headers[0].values[0]: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpResponse.cookies[0].name: should be valid to one and only one schema, but 0 are valid"));
    }

    @Test
    public void shouldValidateInvalidExpectationMissingAction() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("22 error"));
        assertThat(result, containsString("$.httpResponse: is missing, but is required, if specifying action of type Response"));
        assertThat(result, containsString("$.httpError: is missing, but is required, if specifying action of type Error"));
        assertThat(result, containsString("oneOf of the following must be specified [httpError, httpForward"));
    }

    @Test
    public void shouldValidateInvalidDelayTimeUnit() {
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"statusCode\" : 200," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"INVALID\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");
        assertThat(result, containsString("$.httpResponse.delay.timeUnit"));
    }

    @Test
    public void shouldValidateInvalidExtraField() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"invalidField\" : \"randomValue\"" + NEW_LINE +
            "  }");

        // then
        assertThat(result, startsWith("23 error"));
        assertThat(result, containsString("$.invalidField: is not defined in the schema and the schema does not allow additional properties"));
        assertThat(result, containsString("oneOf of the following must be specified [httpError, httpForward"));
    }

    @Test
    public void shouldValidateMultipleInvalidFieldTypes() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"httpRequest\" : \"100\"," + NEW_LINE +
            "    \"httpResponse\" : false" + NEW_LINE +
            "  }");

        // then
        assertThat(result, startsWith("2 error"));
        assertThat(result, containsString("$.httpRequest: string found, object expected"));
        assertThat(result, containsString("$.httpResponse: boolean found, object expected"));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
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
            "}");

        // then
        assertThat(result, startsWith("14 error"));
        assertThat(result, containsString("$.httpRequest.headers: array found, object expected"));
        assertThat(result, containsString("$.httpRequest.headers[0]: string found, object expected"));
        assertThat(result, containsString("$.httpRequest.headers[1]: string found, object expected"));
        assertThat(result, containsString("$.httpResponse.headers: array found, object expected"));
        assertThat(result, containsString("$.httpResponse.headers[0]: string found, object expected"));
        assertThat(result, containsString("$.httpResponse.headers[1]: string found, object expected"));
    }

    @Test
    public void shouldValidateLegacyResponseModifierShape() {
        // a plain (unconditional, single) response modifier still validates — backward compatible
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/some/path\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"headers\" : {" + NEW_LINE +
            "        \"add\" : { \"X-Added\" : [ \"value\" ] }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateConditionalResponseModifier() {
        // a response modifier gated by a condition validates
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/some/path\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"condition\" : {" + NEW_LINE +
            "        \"statusCodeRange\" : \"5xx\"," + NEW_LINE +
            "        \"responseHasHeader\" : \"Content-Type\"" + NEW_LINE +
            "      }," + NEW_LINE +
            "      \"headers\" : {" + NEW_LINE +
            "        \"add\" : { \"X-Server-Error\" : [ \"true\" ] }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateChainedResponseModifiers() {
        // an ordered chain of modifiers validates
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/some/path\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"modifiers\" : [ {" + NEW_LINE +
            "        \"headers\" : { \"add\" : { \"X-Stage\" : [ \"a\" ] } }" + NEW_LINE +
            "      }, {" + NEW_LINE +
            "        \"condition\" : { \"statusCode\" : 200 }," + NEW_LINE +
            "        \"headers\" : { \"replace\" : { \"X-Stage\" : [ \"b\" ] } }" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldRejectResponseModifierConditionWithUnknownProperty() {
        // additionalProperties:false on the condition rejects unknown keys
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/some/path\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
            "    \"responseModifier\" : {" + NEW_LINE +
            "      \"condition\" : { \"notAReal\" : \"x\" }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(not("")));
    }

    @Test
    public void shouldValidateGrpcBidiResponseWithTemplatedEagerMessages() {
        // when — a bidi response with a templateType on an eager message (WS2.6) validates against the schema
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/grpc.Service/BidiStream\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"grpcBidiResponse\" : {" + NEW_LINE +
            "    \"messages\" : [ {" + NEW_LINE +
            "      \"json\" : \"{ \\\"text\\\": \\\"$!request.body\\\" }\"," + NEW_LINE +
            "      \"templateType\" : \"VELOCITY\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldValidateGrpcBidiResponseWithTemplatedRuleResponses() {
        // when — a bidi response with a templateType on a rule response message (WS2.6) validates against the schema
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/grpc.Service/BidiStream\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"grpcBidiResponse\" : {" + NEW_LINE +
            "    \"rules\" : [ {" + NEW_LINE +
            "      \"matchJson\" : \"{ \\\"name\\\": \\\"ping\\\" }\"," + NEW_LINE +
            "      \"responses\" : [ {" + NEW_LINE +
            "        \"json\" : \"{ \\\"reply\\\": \\\"{{ request.body }}\\\" }\"," + NEW_LINE +
            "        \"templateType\" : \"MUSTACHE\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(""));
    }

    @Test
    public void shouldRejectGrpcBidiResponseWithUnknownTemplateType() {
        // when — an unknown templateType is rejected by the schema enum
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "  \"httpRequest\" : {" + NEW_LINE +
            "    \"path\" : \"/grpc.Service/BidiStream\"" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"grpcBidiResponse\" : {" + NEW_LINE +
            "    \"messages\" : [ {" + NEW_LINE +
            "      \"json\" : \"{ }\"," + NEW_LINE +
            "      \"templateType\" : \"NOT_A_REAL_TEMPLATE\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"), is(not("")));
    }
}
