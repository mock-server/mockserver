package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.mock.Expectation;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.HttpStatusCode;

import java.util.Arrays;

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
public class PortForwardingMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        echoServer = new EchoServer(false);
        mockServerPort = new MockServer(echoServer.getPort(), "localhost", 0)
            .getLocalPort();

        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    @Override
    public int getEchoServerPort() {
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
            "resetting all expectations and request logs", //0
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
                "\t}" + NEW_LINE, //1
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_one\",", // 2-0
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
                    "\t}" // 2-1
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
                    "\t  \"path\" : \"/some_path_one\",", // 3-0
                " for action:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}" + NEW_LINE // 3-1
            },
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/not_found\",", // 4-0
                " didn't match expectation:" + NEW_LINE +
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
                    "\t}" + NEW_LINE +
                    NEW_LINE +
                    " because:" + NEW_LINE +
                    NEW_LINE +
                    "\tmethod matched," + NEW_LINE +
                    "\tpath didn't match," + NEW_LINE +
                    "\tquery matched," + NEW_LINE +
                    "\tbody matched," + NEW_LINE +
                    "\theaders matched," + NEW_LINE +
                    "\tcookies matched," + NEW_LINE +
                    "\tkeep-alive matched," + NEW_LINE +
                    "\tssl matched" + NEW_LINE // 4-1
            },
            new String[]{
                "returning response:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"statusCode\" : 404," + NEW_LINE +
                    "\t  \"reasonPhrase\" : \"Not Found\"," + NEW_LINE +
                    "\t  \"headers\" : {", // 5-0
                " for forwarded request" + NEW_LINE +
                    NEW_LINE +
                    " in json:" + NEW_LINE +
                    "" + NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/not_found\"", // 5-1
                " in curl:" // 5-2
            },
            new String[]{
                "request:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"method\" : \"GET\"," + NEW_LINE +
                    "\t  \"path\" : \"/some_path_three\",", // 6-0
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
                    "\t}" // 6-1
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
                    "\t  \"path\" : \"/some_path_three\",", // 7-0
                " for action:" + NEW_LINE +
                    NEW_LINE +
                    "\t{" + NEW_LINE +
                    "\t  \"body\" : \"some_body\"" + NEW_LINE +
                    "\t}" + NEW_LINE // 7-1
            },
            "retrieving logs that match:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"/.*\"" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE // 8-1
        };

        for (int i = 0; i < expectedLogMessages.length; i++) {
            if (expectedLogMessages[i] instanceof String) {
                assertThat("matching log message " + i + "\nActual:\n" + Arrays.toString(actualLogMessages), actualLogMessages[i], endsWith((String) expectedLogMessages[i]));
            } else if (expectedLogMessages[i] instanceof String[]) {
                String[] expectedLogMessage = (String[]) expectedLogMessages[i];
                for (int j = 0; j < expectedLogMessage.length; j++) {
                    assertThat("matching log message " + i + "-" + j + "\nActual:\n" + Arrays.toString(actualLogMessages), actualLogMessages[i], containsString(expectedLogMessage[j]));
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
