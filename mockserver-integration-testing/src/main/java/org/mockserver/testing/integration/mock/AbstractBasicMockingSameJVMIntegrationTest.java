package org.mockserver.testing.integration.mock;

import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequestAndResponse;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationResponseCallback;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.ACCEPTED_202;
import static org.mockserver.model.HttpStatusCode.OK_200;

/**
 * @author jamesdbloom
 */
public abstract class AbstractBasicMockingSameJVMIntegrationTest extends AbstractBasicMockingIntegrationTest {

    @Test
    public void shouldReturnResponseForCallbackClassWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass(PrecannedTestExpectationResponseCallback.class)
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("callback"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldReturnResponseForCallbackClassForSpecifiedClassWithPrecannedResponse() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass(PrecannedTestExpectationResponseCallback.class)
            );

        // then
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
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("echo"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body"),
                    header("x-echo-server-port", insecureEchoServer.getPort())
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestInTestClasspathAsClass() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestInTestClasspathAsString() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestAndResponseInTestClasspath() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequestAndResponse.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequestAndResponse.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldAllowSimultaneousForwardAndResponseExpectations() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo")),
                once()
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                once()
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - forward
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body"),
                HEADERS_TO_IGNORE)
        );
        // - respond
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
        // - no response or forward
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
    }
}
