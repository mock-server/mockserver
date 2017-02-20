package org.mockserver.server;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.netty.websocket.WebSocketException;
import org.mockserver.client.server.ClientException;
import org.mockserver.integration.server.SameJVMAbstractClientServerIntegrationTest;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class DeployableWARAbstractClientServerIntegrationTest extends SameJVMAbstractClientServerIntegrationTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnErrorResponseForExpectationWithObjectCallback() {
        // given
        exception.expect(WebSocketException.class);
        exception.expectMessage(containsString("ExpectationCallback is not supported by MockServer deployable WAR"));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("object_callback"))
                )
                .callback(
                        new ExpectationCallback() {
                            @Override
                            public HttpResponse handle(HttpRequest httpRequest) {
                                return response();
                            }
                        }
                );

    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithConnectionOptions() {
        // given
        exception.expect(ClientException.class);
        exception.expectMessage(containsString("ConnectionOptions is not supported by MockServer deployable WAR"));

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
        exception.expectMessage(containsString("HttpError is not supported by MockServer deployable WAR"));

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
    @SuppressWarnings("Duplicates")
    public void shouldCallbackToSpecifiedClassInTestClasspath() {
        // given
        TestClasspathTestExpectationCallback.httpRequests.clear();
        TestClasspathTestExpectationCallback.httpResponse = response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
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
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationCallback")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldReturnStatus() {
        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + getMockServerPort() + " ]" + System.getProperty("line.separator") +
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + getMockServerSecurePort() + " ]" + System.getProperty("line.separator") +
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
