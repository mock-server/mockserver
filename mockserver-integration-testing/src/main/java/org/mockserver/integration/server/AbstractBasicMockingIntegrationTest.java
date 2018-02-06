package org.mockserver.integration.server;

import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public abstract class AbstractBasicMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    @Test
    public void shouldForwardRequestInHTTP() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(getEchoServerPort())
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
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_https"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldForwardRequestInHTTPS() {
        EchoServer secureEchoServer = new EchoServer(true);

        try {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    forward()
                        .withHost("127.0.0.1")
                        .withPort(secureEchoServer.getPort())
                        .withScheme(HttpForward.Scheme.HTTPS)
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
                    .withBody("an_example_body_http"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    headersToIgnore)
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        } finally {
            secureEchoServer.stop();
        }
    }

    @Test
    public void shouldForwardOverriddenRequest() {
        // given
        EchoServer echoServer = new EchoServer(false);
        EchoServer secureEchoServer = new EchoServer(true);

        try {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                        .withSecure(false)
                )
                .forward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + echoServer.getPort())
                            .withBody("some_overridden_body")
                    ).withDelay(MILLISECONDS, 10)
                );
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                        .withSecure(true)
                )
                .forward(
                    forwardOverriddenRequest(
                        request()
                            .withHeader("Host", "localhost:" + secureEchoServer.getPort())
                            .withBody("some_overridden_body")
                    ).withDelay(MILLISECONDS, 10)
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
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    headersToIgnore

                )
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("some_overridden_body"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body_https")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        } finally {
            echoServer.stop();
            secureEchoServer.stop();
        }
    }

    @Test
    public void shouldCallbackForForwardToSpecifiedClassWithPrecannedResponse() {
        // given
        EchoServer echoServer = new EchoServer(false);
        EchoServer secureEchoServer = new EchoServer(true);

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.integration.callback.PrecannedTestExpectationForwardCallback")
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
                        header("x-echo-server-port", echoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
            )
        );

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
                headersToIgnore
            )
        );
    }

    @Test
    public void shouldForwardTemplateInVelocity() {
        EchoServer secureEchoServer = new EchoServer(false);
        try {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    template(HttpTemplate.TemplateType.VELOCITY,
                        "{" + NEW_LINE +
                            "    'path' : \"/somePath\"," + NEW_LINE +
                            "    'headers' : [ {" + NEW_LINE +
                            "        'name' : \"Host\"," + NEW_LINE +
                            "        'values' : [ \"127.0.0.1:" + secureEchoServer.getPort() + "\" ]" + NEW_LINE +
                            "    }, {" + NEW_LINE +
                            "        'name' : \"x-test\"," + NEW_LINE +
                            "        'values' : [ \"$!request.headers['x-test'][0]\" ]" + NEW_LINE +
                            "    } ]," + NEW_LINE +
                            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                            "}")
                        .withDelay(MILLISECONDS, 10)
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
                    .withBody("{'name': 'value'}"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    headersToIgnore
                )
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("{'name': 'value'}"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body_https")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        } finally {
            secureEchoServer.stop();
        }
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
                    .withPort(getEchoServerPort())
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
                headersToIgnore)
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
                headersToIgnore)
        );
        // - no response or forward
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldCallbackForResponseToSpecifiedClassWithPrecannedResponse() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass("org.mockserver.integration.callback.PrecannedTestExpectationResponseCallback")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
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
                headersToIgnore
            )
        );

        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
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
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore
            )
        );
    }

    @Test
    public void shouldSupportBatchedExpectations() throws Exception {
        // when
        new NettyHttpClient().sendRequest(
            request()
                .withMethod("PUT")
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("/expectation"))
                .withBody("" +
                    "[" +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_one"), once(), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body_one"))
                        ) + "," +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_two"), once(), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body_two"))
                        ) + "," +
                    new ExpectationSerializer(new MockServerLogger())
                        .serialize(
                            new Expectation(request("/path_three"), once(), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body_three"))
                        ) +
                    "]"
                )
        ).get(10, TimeUnit.SECONDS);

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_one"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_one")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_two"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_two")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_three"),
            makeRequest(
                request()
                    .withPath(calculatePath("/path_three")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithOnlyBody() {
        // when
        mockServerClient.when(request()).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithOnlyStatusCode() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingStringBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withBody(
                        exact("some_random_body")
                    ),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_string_body_response")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_string_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_string_body_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withBody("some_random_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForExpectationWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
                    .withDelay(new Delay(MILLISECONDS, 10))
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
                    .withDelay(new Delay(MILLISECONDS, 20))
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseFromVelocityTemplate() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                template(
                    HttpTemplate.TemplateType.VELOCITY,
                    "{" + NEW_LINE +
                        "     \"statusCode\": 200," + NEW_LINE +
                        "     \"headers\": [ { \"name\": \"name\", \"values\": [ \"$!request.headers['name'][0]\" ] } ]," + NEW_LINE +
                        "     \"body\": \"$!request.body\"" + NEW_LINE +
                        "}" + NEW_LINE
                )
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("name", "value")
                .withBody("some_request_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withHeader("name", "value")
                    .withBody("some_request_body"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("name", "value")
                .withBody("some_request_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withHeader("name", "value")
                    .withBody("some_request_body"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethod() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_pathRequest"))
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
                    .withPath(calculatePath("some_pathRequest"))
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
                    .withPath(calculatePath("some_pathRequest"))
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
    public void shouldNotReturnResponseForNonMatchingBody() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_other_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_other_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingPath() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body")
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("some_other_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withSecure(true)
                    .withPath(calculatePath("some_other_path"))
                    .withQueryStringParameters(
                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                    )
                    .withBody(exact("some_body"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldVerifyReceivedRequests() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.atLeast(1));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.exactly(2));
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                .withPath(calculatePath("some_path")), VerificationTimes.atLeast(2));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was:<{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"," + NEW_LINE));
        }
    }

    @Test
    public void shouldVerifyNoRequestsReceived() {
        // when
        mockServerClient.reset();

        // then
        mockServerClient.verifyZeroInteractions();
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldRetrieveRecordedRequests() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );

        // then
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*"))),
            request(calculatePath("some_path_one")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request()),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );
    }

    @Test
    public void shouldRetrieveActiveExpectations() {
        // when
        HttpRequest complexRequest = request()
            .withPath(calculatePath("some_path.*"))
            .withHeader("some", "header")
            .withQueryStringParameter("some", "parameter")
            .withCookie("some", "parameter")
            .withBody("some_body");
        mockServerClient.when(complexRequest, exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_path.*")))
            .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_other_path")))
            .respond(response().withBody("some_other_body"));
        mockServerClient.when(request().withPath(calculatePath("some_forward_path")))
            .forward(forward());

        // then
        assertThat(
            mockServerClient.retrieveActiveExpectations(request().withPath(calculatePath("some_path.*"))),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited())
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body"))
            )
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited())
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );

        assertThat(
            mockServerClient.retrieveActiveExpectations(request()),
            arrayContaining(
                new Expectation(complexRequest, exactly(4), TimeToLive.unlimited())
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_path.*")))
                    .thenRespond(response().withBody("some_body")),
                new Expectation(request().withPath(calculatePath("some_other_path")))
                    .thenRespond(response().withBody("some_other_body")),
                new Expectation(request().withPath(calculatePath("some_forward_path")))
                    .thenForward(forward())
            )
        );
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // when
        EchoServer secureEchoServer = new EchoServer(false);
        try {
            mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
            );
            HttpRequest complexRequest = request()
                .withPath(calculatePath("some_path_one"))
                .withHeader("some", "header")
                .withQueryStringParameter("some", "parameter")
                .withCookie("some", "parameter")
                .withBody("some_body_one");
            assertEquals(
                response("some_body_one")
                    .withHeader("some", "header")
                    .withHeader("cookie", "some=parameter")
                    .withHeader("set-cookie", "some=parameter")
                    .withCookie("some", "parameter"),
                makeRequest(
                    complexRequest,
                    headersToIgnore
                )
            );
            assertEquals(
                response("some_body_three"),
                makeRequest(
                    request()
                        .withPath(calculatePath("some_path_three"))
                        .withBody("some_body_three"),
                    headersToIgnore
                )
            );

            // then
            Expectation[] recordedExpectations = mockServerClient.retrieveRecordedExpectations(request().withPath(calculatePath("some_path_one")));
            assertThat(recordedExpectations.length, is(1));
            verifyRequestsMatches(
                new HttpRequest[]{
                    recordedExpectations[0].getHttpRequest()
                },
                request(calculatePath("some_path_one")).withBody("some_body_one")
            );
            assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
            // and
            recordedExpectations = mockServerClient.retrieveRecordedExpectations(request());
            assertThat(recordedExpectations.length, is(2));
            verifyRequestsMatches(
                new HttpRequest[]{
                    recordedExpectations[0].getHttpRequest(),
                    recordedExpectations[1].getHttpRequest()
                },
                request(calculatePath("some_path_one")).withBody("some_body_one"),
                request(calculatePath("some_path_three")).withBody("some_body_three")
            );
            assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
            assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
            // and
            recordedExpectations = mockServerClient.retrieveRecordedExpectations(null);
            assertThat(recordedExpectations.length, is(2));
            verifyRequestsMatches(
                new HttpRequest[]{
                    recordedExpectations[0].getHttpRequest(),
                    recordedExpectations[1].getHttpRequest()
                },
                request(calculatePath("some_path_one")).withBody("some_body_one"),
                request(calculatePath("some_path_three")).withBody("some_body_three")
            );
            assertThat(recordedExpectations[0].getHttpResponse().getBodyAsString(), is("some_body_one"));
            assertThat(recordedExpectations[1].getHttpResponse().getBodyAsString(), is("some_body_three"));
        } finally {
            secureEchoServer.stop();
        }
    }

    @Test
    public void shouldRetrieveRecordedLogMessages() {
        // when
        mockServerClient.reset();
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                headersToIgnore)
        );

        // then
        String[] actualLogMessages = mockServerClient.retrieveLogMessagesArray(request().withPath(calculatePath(".*")));

        Object[] expectedLogMessages = new Object[]{
            "resetting all expectations and request logs" + NEW_LINE,
            "creating expectation:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"httpRequest\" : {" + NEW_LINE +
                "\t    \"path\" : \"/some_path.*\"" + NEW_LINE +
                "\t  }," + NEW_LINE +
                "\t  \"times\" : {" + NEW_LINE +
                "\t    \"remainingTimes\" : 4" + NEW_LINE +
                "\t  }," + NEW_LINE +
                "\t  \"timeToLive\" : {" + NEW_LINE +
                "\t    \"unlimited\" : true" + NEW_LINE +
                "\t  }," + NEW_LINE +
                "\t  \"httpResponse\" : {" + NEW_LINE +
                "\t    \"body\" : \"some_body\"" + NEW_LINE +
                "\t  }" + NEW_LINE +
                "\t}" + NEW_LINE,
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_one\",",
                " matched expectation:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"httpRequest\" : {" + NEW_LINE +
                    "\t    \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"times\" : {" + NEW_LINE +
                    "\t    \"remainingTimes\" : 4" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"timeToLive\" : {" + NEW_LINE +
                    "\t    \"unlimited\" : true" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"httpResponse\" : {" + NEW_LINE +
                    "\t    \"body\" : \"some_body\"" + NEW_LINE +
                    "\t  }" + NEW_LINE +
                    "\t}"
            },
            new String[]{
                "returning response:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"headers\" : {" + NEW_LINE +
                    "\t    \"connection\" : [ \"keep-alive\" ]" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}" + NEW_LINE +
                    NEW_LINE +
                    " for request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_one\",",
                " for expectation:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"httpRequest\" : {" + NEW_LINE +
                    "\t    \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"times\" : {" + NEW_LINE +
                    "\t    \"remainingTimes\" : 3" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"timeToLive\" : {" + NEW_LINE +
                    "\t    \"unlimited\" : true" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"httpResponse\" : {" + NEW_LINE +
                    "\t    \"body\" : \"some_body\"" + NEW_LINE +
                    "\t  }" + NEW_LINE +
                    "\t}" + NEW_LINE
            },
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/not_found\",",
                " did not match expectation:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "\t}" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "\tmethod matches = true" + NEW_LINE +
                    "\tpath matches = false" + NEW_LINE +
                    "\tquery string parameters match = true" + NEW_LINE +
                    "\tbody matches = true" + NEW_LINE +
                    "\theaders match = true" + NEW_LINE +
                    "\tcookies match = true" + NEW_LINE +
                    "\tkeep-alive matches = true" + NEW_LINE +
                    "\tssl matches = true"
            },
            new String[]{
                "no matching expectation - returning:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"statusCode\" : 404," + NEW_LINE +
                    "\t  \"reasonPhrase\" : \"Not Found\"" + NEW_LINE +
                    "\t}" + NEW_LINE +
                    NEW_LINE +
                    " for request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/not_found\","
            },
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_three\",",
                " matched expectation:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"httpRequest\" : {" + NEW_LINE +
                    "\t    \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"times\" : {" + NEW_LINE +
                    "\t    \"remainingTimes\" : 3" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"timeToLive\" : {" + NEW_LINE +
                    "\t    \"unlimited\" : true" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"httpResponse\" : {" + NEW_LINE +
                    "\t    \"body\" : \"some_body\"" + NEW_LINE +
                    "\t  }" + NEW_LINE +
                    "\t}"
            },
            new String[]{
                "returning response:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"headers\" : {" + NEW_LINE +
                    "\t    \"connection\" : [ \"keep-alive\" ]" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}" + NEW_LINE +
                    NEW_LINE +
                    " for request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_three\",",
                " for expectation:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"httpRequest\" : {" + NEW_LINE +
                    "\t    \"path\" : \"/some_path.*\"" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"times\" : {" + NEW_LINE +
                    "\t    \"remainingTimes\" : 2" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"timeToLive\" : {" + NEW_LINE +
                    "\t    \"unlimited\" : true" + NEW_LINE +
                    "\t  }," + NEW_LINE +
                    "\t  \"httpResponse\" : {" + NEW_LINE +
                    "\t    \"body\" : \"some_body\"" + NEW_LINE +
                    "\t  }" + NEW_LINE +
                    "\t}" + NEW_LINE
            },
            "retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"/.*\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE
        };

        for (int i = 0; i < expectedLogMessages.length; i++) {
            if (expectedLogMessages[i] instanceof String) {
                assertThat("matching log message " + i, actualLogMessages[i], endsWith((String) expectedLogMessages[i]));
            } else if (expectedLogMessages[i] instanceof String[]) {
                String[] expectedLogMessage = (String[]) expectedLogMessages[i];
                for (int j = 0; j < expectedLogMessage.length; j++) {
                    assertThat("matching log message " + i + "-" + j, actualLogMessages[i], containsString(expectedLogMessage[j]));
                }
            }
        }
    }

    @Test
    public void shouldClearExpectationsAndLogs() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1"))
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
        );

        // and then - remaining expectations not cleared
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReset() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // when
        mockServerClient.reset();

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnErrorForInvalidExpectation() throws Exception {
        // when
        HttpResponse httpResponse = new NettyHttpClient().sendRequest(
            request()
                .withMethod("PUT")
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("/expectation"))
                .withBody("{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/path_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"incorrectField\" : {" + NEW_LINE +
                    "    \"body\" : \"some_body_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"remainingTimes\" : 1" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}")
        ).get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("2 errors:" + NEW_LINE +
            " - object instance has properties which are not allowed by the schema: [\"incorrectField\"]" + NEW_LINE +
            " - oneOf of the following must be specified \"httpResponse\" \"httpResponseTemplate\" \"httpResponseObjectCallback\" \"httpResponseClassCallback\" \"httpForward\" \"httpForwardTemplate\" \"httpForwardObjectCallback\" \"httpForwardClassCallback\" \"httpOverrideForwardedRequest\" \"httpError\" "));
    }

    @Test
    public void shouldReturnErrorForInvalidRequest() throws Exception {
        // when
        HttpResponse httpResponse = new NettyHttpClient().sendRequest(
            request()
                .withMethod("PUT")
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("/clear"))
                .withBody("{" + NEW_LINE +
                    "    \"path\" : 500," + NEW_LINE +
                    "    \"method\" : true," + NEW_LINE +
                    "    \"keepAlive\" : \"false\"" + NEW_LINE +
                    "  }")
        ).get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("3 errors:" + NEW_LINE +
            " - instance type (string) does not match any allowed primitive type (allowed: [\"boolean\"]) for field \"/keepAlive\"" + NEW_LINE +
            " - instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"]) for field \"/method\"" + NEW_LINE +
            " - instance type (integer) does not match any allowed primitive type (allowed: [\"string\"]) for field \"/path\""));
    }
}
