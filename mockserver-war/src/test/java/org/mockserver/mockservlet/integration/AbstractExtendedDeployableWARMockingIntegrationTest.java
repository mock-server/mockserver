package org.mockserver.mockservlet.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.ClientException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.testing.integration.mock.AbstractExtendedSameJVMMockingIntegrationTest;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.ACCEPTED_202;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public abstract class AbstractExtendedDeployableWARMockingIntegrationTest extends AbstractExtendedSameJVMMockingIntegrationTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnResponseByMatchingUrlEncodedPath() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("ab@c.de"))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("ab%40c.de"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withSecure(true)
                    .withPath(calculatePath("ab%40c.de"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withHeaders(header("headerNameRequest", "headerValueRequest"))
                    .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithConnectionOptions() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ConnectionOptions is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody("some_long_body")
                    .withConnectionOptions(
                        connectionOptions()
                            .withKeepAliveOverride(true)
                            .withContentLengthHeaderOverride(10)
                    )
            );
    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithHttpError() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("HttpError is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
            )
            .error(
                error()
                    .withDropConnection(true)
            );
    }

    @Test
    public void shouldReturnErrorResponseForRespondByObjectCallback() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ExpectationResponseCallback, ExpectationForwardCallback or ExpectationForwardAndResponseCallback is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback")),
                once()
            )
            .respond(
                httpRequest -> response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withHeaders(
                        header("x-object-callback", "test_object_callback_header")
                    )
                    .withBody("an_object_callback_response")
            );
    }

    @Test
    public void shouldReturnErrorResponseForForwardByObjectCallback() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ExpectationResponseCallback, ExpectationForwardCallback or ExpectationForwardAndResponseCallback is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest -> request()
                    .withBody("some_overridden_body")
                    .withSecure(httpRequest.isSecure())
            );
    }

    @Test
    public void shouldReturnErrorResponseForForwardAndResponseByObjectCallback() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ExpectationResponseCallback, ExpectationForwardCallback or ExpectationForwardAndResponseCallback is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest ->
                    request()
                        .withBody("some_overridden_body")
                        .withSecure(httpRequest.isSecure()),
                (httpRequest, httpResponse) ->
                    httpResponse
                        .withHeader("x-response-test", "x-response-test")
            );
    }

    @Test
    public void shouldCallbackForResponseToSpecifiedClassInTestClasspath() {
        // given
        TestClasspathTestExpectationResponseCallback.httpRequests.clear();
        TestClasspathTestExpectationResponseCallback.httpResponse = response()
            .withStatusCode(ACCEPTED_202.code())
            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
            .withHeaders(
                header("x-callback", "test_callback_header")
            )
            .withBody("a_callback_response");

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass("org.mockserver.mockservlet.integration.TestClasspathTestExpectationResponseCallback")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldCallbackForwardCallbackToOverrideRequestInTestClasspath() {
        // given
        TestClasspathTestExpectationForwardCallback.httpRequests.clear();
        TestClasspathTestExpectationForwardCallback.httpRequestToReturn = request()
            .withHeaders(
                header("x-callback", "test_callback_header"),
                header("Host", "localhost:" + insecureEchoServer.getPort())
            )
            .withBody("a_callback_forward");

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.mockservlet.integration.TestClasspathTestExpectationForwardCallback")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_forward"),
            makeRequest(
                request()
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationForwardCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationForwardCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_forward"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationForwardCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationForwardCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldCallbackForwardCallbackToOverrideRequestAndResponseInTestClasspath() {
        // given
        TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequests.clear();
        TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequestToReturn = request()
            .withHeaders(
                header("x-callback", "test_callback_header_request"),
                header("Host", "localhost:" + insecureEchoServer.getPort())
            )
            .withBody("a_callback_forward_request");
        TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpResponseToReturn = response()
            .withHeaders(
                header("x-callback", "test_callback_header_response")
            )
            .withBody("a_callback_forward_response");

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.mockservlet.integration.TestClasspathTestExpectationForwardCallbackWithResponseOverride")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header_response")
                )
                .withBody("a_callback_forward_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpResponses.get(0).getBody().getValue(), "a_callback_forward_request");

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header_response")
                )
                .withBody("a_callback_forward_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("X-Test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationForwardCallbackWithResponseOverride.httpResponses.get(0).getBody().getValue(), "a_callback_forward_request");
    }

    @Test
    public void shouldReturnStatus() {
        // given
        PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(getServerPort())
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(getServerSecurePort())
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
    }


    @Test
    public void shouldReturnStatusOnCustomPath() {
        String originalStatusPath = ConfigurationProperties.livenessHttpGetPath();
        try {
            // given
            ConfigurationProperties.livenessHttpGetPath("/livenessProbe");
            PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());
            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                    .withBody(json(portBindingSerializer.serialize(
                        portBinding(getServerPort())
                    ), MediaType.JSON_UTF_8)),
                makeRequest(
                    request()
                        .withPath(calculatePath("livenessProbe"))
                        .withMethod("GET"),
                    headersToIgnore)
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                    .withBody(json(portBindingSerializer.serialize(
                        portBinding(getServerSecurePort())
                    ), MediaType.JSON_UTF_8)),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("livenessProbe"))
                        .withMethod("GET"),
                    headersToIgnore)
            );
        } finally {
            ConfigurationProperties.livenessHttpGetPath(originalStatusPath);
        }
    }
}
