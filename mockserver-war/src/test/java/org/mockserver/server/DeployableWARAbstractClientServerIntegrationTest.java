package org.mockserver.server;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.ClientException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.SameJVMAbstractClientServerIntegrationTest;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.ACCEPTED_202;
import static org.mockserver.model.HttpStatusCode.OK_200;

/**
 * @author jamesdbloom
 */
public abstract class DeployableWARAbstractClientServerIntegrationTest extends SameJVMAbstractClientServerIntegrationTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

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
        exception.expectMessage(containsString("ExpectationResponseCallback and ExpectationForwardCallback is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback"))
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) {
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                            .withHeaders(
                                header("x-object-callback", "test_object_callback_header")
                            )
                            .withBody("an_object_callback_response");
                    }
                }
            );
    }

    @Test
    public void shouldReturnErrorResponseForForwardByObjectCallback() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ExpectationResponseCallback and ExpectationForwardCallback is not supported by MockServer deployed as a WAR"));

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                new ExpectationForwardCallback() {
                    @Override
                    public HttpRequest handle(HttpRequest httpRequest) {
                        return request()
                            .withBody("some_overridden_body")
                            .withSecure(httpRequest.isSecure());
                    }
                }
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
                    .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationResponseCallback")
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
    public void shouldCallbackForForwardCallbackToSpecifiedClassInTestClasspath() {
        // given
        EchoServer secureEchoServer = new EchoServer(false);
        System.out.println("secureEchoServer.getPort() = " + secureEchoServer.getPort());
        TestClasspathTestExpectationForwardCallback.httpRequests.clear();
        TestClasspathTestExpectationForwardCallback.httpRequestToReturn = request()
            .withHeaders(
                header("x-callback", "test_callback_header"),
                header("Host", "localhost:" + secureEchoServer.getPort())
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
                    .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationForwardCallback")
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
    public void shouldReturnStatus() {
        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + getMockServerPort() + " ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withPath(calculatePath("status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + getMockServerSecurePort() + " ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
    }
}
