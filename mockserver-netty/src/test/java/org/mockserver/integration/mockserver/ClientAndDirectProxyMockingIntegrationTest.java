package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class ClientAndDirectProxyMockingIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {

        // start echo servers
        echoServer = new EchoServer(false);
        // start direct proxy and client
        new ProxyBuilder()
            .withLocalPort(SERVER_HTTP_PORT)
            .withDirect("localhost", echoServer.getPort())
            .build();
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
        echoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }

    @Test
    @Override
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
                    .withPort(getTestServerPort())
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
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                headersToIgnore)
        );
    }

    @Test
    @Override
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
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
    }

    @Test
    @Override
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
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                headersToIgnore)
        );
    }

    @Test
    @Override
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
                    "\t  \"path\" : \"/some_path.*\"" + NEW_LINE +
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
                " for response action:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}"
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
                "returning response:" + NEW_LINE +
                    "" + NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"statusCode\" : 404," + NEW_LINE +
                    "\t  \"reasonPhrase\" : \"Not Found\"," + NEW_LINE +
                    "\t  \"headers\" : {",
                "for request:" + NEW_LINE +
                    "" + NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/not_found\""
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
                    "\t  \"path\" : \"/some_path.*\"" + NEW_LINE +
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
                " for response action:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}"
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
    @Override
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
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase())
                .withBody(exact("some_other_body"))
                .withHeader(header("set-cookie", "cookieName=cookieValue"))
                .withHeader(header("headerName", "headerValue"))
                .withHeader(header("cookie", "cookieName=cookieValue"))
                .withCookies(cookie("cookieName", "cookieValue")),
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
    }

    @Test
    @Override
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
                .withStatusCode(HttpStatusCode.OK_200.code())
                .withReasonPhrase(HttpStatusCode.OK_200.reasonPhrase())
                .withBody(exact("some_body"))
                .withHeader(header("set-cookie", "cookieName=cookieValue"))
                .withHeader(header("headerName", "headerValue"))
                .withHeader(header("cookie", "cookieName=cookieValue"))
                .withCookies(cookie("cookieName", "cookieValue")),
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
    }
}
