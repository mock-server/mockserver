package org.mockserver.integration.mocking;

import com.google.common.collect.ImmutableList;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.mock.Expectation;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
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
@Ignore
public class LoadBalancerMockingIntegrationTest {

    private static int loadBalancerPort;
    private static int mockServerAPIPort;
    private static EchoServer echoServer;
    private static ClientAndServer loadBalancerClient;
    private static MockServerClient mockServerClient;

    private static NettyHttpClient httpClient = new NettyHttpClient();
    private Integer echoServerPort;

    @BeforeClass
    public static void startServer() {
        echoServer = new EchoServer(false);

        MockServer mockServer = new MockServer("127.0.0.1", echoServer.getPort());
        loadBalancerClient = startClientAndServer("127.0.0.1", mockServer.getLocalPort());
        loadBalancerClient
            .when(request())
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader(HOST.toString(), "127.0.0.1" + mockServer.getLocalPort())
                )
            );

        mockServerAPIPort = mockServer.getLocalPort();
        loadBalancerPort = loadBalancerClient.getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        if (loadBalancerClient != null) {
            loadBalancerClient.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    private static List<String> headersToIgnore = ImmutableList.of(
        HttpHeaderNames.SERVER.toString(),
        HttpHeaderNames.EXPIRES.toString(),
        HttpHeaderNames.DATE.toString(),
        HttpHeaderNames.HOST.toString(),
        HttpHeaderNames.CONNECTION.toString(),
        HttpHeaderNames.USER_AGENT.toString(),
        HttpHeaderNames.CONTENT_LENGTH.toString(),
        HttpHeaderNames.ACCEPT_ENCODING.toString(),
        HttpHeaderNames.TRANSFER_ENCODING.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(),
        HttpHeaderNames.VARY.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(),
        "version",
        "x-cors"
    );

    private void verifyRequestsMatches(HttpRequest[] httpRequests, HttpRequest... httpRequestMatchers) {
        if (httpRequests.length != httpRequestMatchers.length) {
            throw new AssertionError("Number of request matchers does not match number of requests, expected:<" + httpRequestMatchers.length + "> but was:<" + httpRequests.length + ">");
        } else {
            for (int i = 0; i < httpRequestMatchers.length; i++) {
                if (!new HttpRequestMatcher(httpRequestMatchers[i], new MockServerLogger(this.getClass())).matches(null, httpRequests[i])) {
                    throw new AssertionError("Request does not match request matcher, expected:<" + httpRequestMatchers[i] + "> but was:<" + httpRequests[i] + ">");
                }
            }
        }
    }

    private HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        try {
            boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
            int port = loadBalancerPort;
            httpRequest.withPath(httpRequest.getPath().getValue());
            httpRequest.withHeader(HOST.toString(), "localhost:" + port);
            boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
            HttpResponse httpResponse = httpClient.sendRequest(httpRequest, new InetSocketAddress("localhost", port))
                .get(30, (isDebug ? TimeUnit.MINUTES : TimeUnit.SECONDS));
            Headers headers = new Headers();
            for (Header header : httpResponse.getHeaderList()) {
                if (!headersToIgnore.contains(header.getName().getValue().toLowerCase())) {
                    if (header.getName().getValue().equalsIgnoreCase(CONTENT_TYPE.toString())) {
                        // this fixes Tomcat which removes the space between
                        // media type and charset in the Content-Type header
                        for (NottableString value : new ArrayList<NottableString>(header.getValues())) {
                            header.getValues().clear();
                            header.addValues(value.getValue().replace(";charset", "; charset"));
                        }
                        header = header(header.getName().lowercase(), header.getValues());
                    }
                    headers.withEntry(header);
                }
            }
            httpResponse.withHeaders(headers);
            return httpResponse;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void shouldForwardRequestInHTTP() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/echo")
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(echoServerPort)
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
                    .withPath("/echo")
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
                    .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                        .withPath("/echo")
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
                    .withPath("/"),
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
                    .withPath("/"),
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
                    .withPath("/some_path")
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
                    .withPath("/some_path")
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
                    .withPath("/some_path")
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
    public void shouldVerifyReceivedRequests() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path"), exactly(2)
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
                    .withPath("/some_path"),
                headersToIgnore)
        );
        mockServerClient.verify(request()
            .withPath("/some_path"));
        mockServerClient.verify(request()
            .withPath("/some_path"), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath("/some_path"),
                headersToIgnore)
        );
        mockServerClient.verify(request().withPath("/some_path"), VerificationTimes.atLeast(1));
        mockServerClient.verify(request().withPath("/some_path"), VerificationTimes.exactly(2));
    }

    @Test
    public void shouldRetrieveRecordedLogMessages() {
        // when
        mockServerClient.reset();
        mockServerClient.when(request().withPath("/some_path.*"), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath("/some_path_one"),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath("/not_found"),
                headersToIgnore)
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath("/some_path_three"),
                headersToIgnore)
        );

        // then
        String[] actualLogMessages = mockServerClient.retrieveLogMessagesArray(request().withPath("/.*"));

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
                    .withPath("/some_path1")
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath("/some_path2")
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
                    .withPath("/some_path1"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath("/some_path2"),
                headersToIgnore)
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath("/some_path1")
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath("/some_path2"))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request("/some_path2")
        );

        // and then - remaining expectations not cleared
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath("/some_path2"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath("/some_path1"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReset() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath("/some_path1")
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath("/some_path2")
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
                    .withPath("/some_path1"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath("/some_path2"),
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
                    .withPath("/some_path1"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath("/some_path2"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnErrorForInvalidExpectation() throws Exception {
        // when
        HttpResponse httpResponse = new NettyHttpClient().sendRequest(
            request()
                .withMethod("PUT")
                .withHeader(HOST.toString(), "localhost:" + mockServerAPIPort)
                .withPath("/expectation")
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

}
