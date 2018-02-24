package org.mockserver.client;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.filters.MockServerEventLog;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.Scheme.HTTPS;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;

/**
 * @author jamesdbloom
 */
public class MockServerClientIntegrationTest {

    private static MockServerClient mockServerClient;
    private static EchoServer echoServer;
    private static MockServerEventLog logFilter;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void startEchoServer() {
        echoServer = new EchoServer(false);
        logFilter = echoServer.requestLogFilter();
        mockServerClient = new MockServerClient("localhost", echoServer.getPort());
    }

    @AfterClass
    public static void stopEchoServer() {
        echoServer.stop();
    }

    @Before
    public void clearRequestLog() {
        logFilter.reset();
    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponse\" : {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"responseName\" : [ \"responseValue\" ]" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"body\" : \"some_response_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseTemplate() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                template(HttpTemplate.TemplateType.VELOCITY)
                    .withTemplate("some_response_template")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponseTemplate\" : {" + NEW_LINE +
                    "    \"template\" : \"some_response_template\"," + NEW_LINE +
                    "    \"templateType\" : \"VELOCITY\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseClassCallback() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                callback()
                    .withCallbackClass("some_class")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponseClassCallback\" : {" + NEW_LINE +
                    "    \"callbackClass\" : \"some_class\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseObjectCallback() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                callback()
                    .withCallbackClass("some_class")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponseClassCallback\" : {" + NEW_LINE +
                    "    \"callbackClass\" : \"some_class\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                forward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HTTPS)
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpForward\" : {" + NEW_LINE +
                    "    \"host\" : \"some_host\"," + NEW_LINE +
                    "    \"port\" : 9090," + NEW_LINE +
                    "    \"scheme\" : \"HTTPS\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardTemplate() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                template(HttpTemplate.TemplateType.VELOCITY)
                    .withTemplate("some_response_template")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpForwardTemplate\" : {" + NEW_LINE +
                    "    \"template\" : \"some_response_template\"," + NEW_LINE +
                    "    \"templateType\" : \"VELOCITY\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardClassCallback() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                callback()
                    .withCallbackClass("some_class")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpForwardClassCallback\" : {" + NEW_LINE +
                    "    \"callbackClass\" : \"some_class\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardObjectCallback() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                callback()
                    .withCallbackClass("some_class")
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpForwardClassCallback\" : {" + NEW_LINE +
                    "    \"callbackClass\" : \"some_class\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithOverrideForwardedRequest() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("host", "localhost:" + echoServer.getPort())
                        .withBody("some_override_body")
                )
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
                    "    \"httpRequest\" : {" + NEW_LINE +
                    "      \"headers\" : {" + NEW_LINE +
                    "        \"host\" : [ \"localhost:" + echoServer.getPort() + "\" ]" + NEW_LINE +
                    "      }," + NEW_LINE +
                    "      \"body\" : \"some_override_body\"" + NEW_LINE +
                    "    }" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithError() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .error(
                error()
                    .withDropConnection(true)
                    .withResponseBytes("silly_bytes".getBytes(UTF_8))
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpError\" : {" + NEW_LINE +
                    "    \"dropConnection\" : true," + NEW_LINE +
                    "    \"responseBytes\" : \"c2lsbHlfYnl0ZXM=\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendExpectationRequestWithExactTimes() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                exactly(3)
            )
            .respond(
                response()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponse\" : {" + NEW_LINE +
                    "    \"headers\" : {" + NEW_LINE +
                    "      \"responseName\" : [ \"responseValue\" ]" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"body\" : \"some_response_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"remainingTimes\" : 3" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendStopRequest() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.stop();

        // then
        String result = logFilter.verify(verificationSequence().withRequests(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/stop")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive")
                )
                .withSecure(false)
                .withKeepAlive(true),
            request()
                .withMethod("PUT")
                .withPath("/mockserver/status")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldQueryRunningStatus() {
        // given
        echoServer.withNextResponse(response().withStatusCode(200));

        // when
        boolean isRunning = mockServerClient.isRunning();

        // then
        assertThat(isRunning, is(true));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/status")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldQueryRunningStatusWhenNotRunning() {
        // given
        int numberOfRetries = 11;
        HttpResponse[] httpResponses = new HttpResponse[numberOfRetries];
        Arrays.fill(httpResponses, response().withStatusCode(404));
        echoServer.withNextResponse(httpResponses);

        // when
        boolean isRunning = mockServerClient.isRunning();

        // then
        assertThat(isRunning, is(false));
        assertThat(logFilter.retrieveRequests(request()).size(), is(numberOfRetries));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/status")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendResetRequest() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.reset();

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/reset")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequest() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .clear(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequestWithType() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
            .clear(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                ClearType.LOG
            );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequestForNullRequest() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.clear(null);

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequests() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new HttpRequestSerializer(new MockServerLogger()).serialize(Arrays.asList(
                    request("/some_request_one"),
                    request("/some_request_two")
                ))))
        );

        // when
        HttpRequest[] actualResponse = mockServerClient.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequestsWithNullRequest() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new HttpRequestSerializer(new MockServerLogger()).serialize(Arrays.asList(
                    request("/some_request_one"),
                    request("/some_request_two")
                ))))
        );

        // when
        HttpRequest[] actualResponse = mockServerClient.retrieveRecordedRequests(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequestsAsJson() {
        // given
        String serializedRequests = new HttpRequestSerializer(new MockServerLogger()).serialize(Arrays.asList(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializedRequests))
        );

        // when
        String recordedResponse = mockServerClient.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(recordedResponse, is(serializedRequests));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequestsAsJava() {
        // given
        String serializedRequest = new HttpRequestToJavaSerializer().serialize(Arrays.asList(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializedRequest))
        );

        // when
        String actualResponse = mockServerClient.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedRequest));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveActiveExpectations() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new ExpectationSerializer(new MockServerLogger()).serialize(
                    new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                    new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveActiveExpectationsWithNullRequest() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new ExpectationSerializer(new MockServerLogger()).serialize(
                    new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                    new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveActiveExpectations(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJson() {
        // given
        String serializeExpectations = new ExpectationSerializer(new MockServerLogger()).serialize(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        );
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializeExpectations))
        );

        // when
        String actualResponse = mockServerClient.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(actualResponse, is(serializeExpectations));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveActiveExpectationsAsJava() {
        // given
        String serializedExpectations = new ExpectationToJavaSerializer().serialize(Arrays.asList(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializedExpectations))
        );

        // when
        String actualResponse = mockServerClient.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedExpectations));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new ExpectationSerializer(new MockServerLogger()).serialize(
                    new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                    new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRecordedExpectationsWithNullRequest() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(new ExpectationSerializer(new MockServerLogger()).serialize(
                    new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                    new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveRecordedExpectations(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJson() {
        // given
        String serializeExpectations = new ExpectationSerializer(new MockServerLogger()).serialize(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        );
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializeExpectations))
        );

        // when
        String actualResponse = mockServerClient.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(actualResponse, is(serializeExpectations));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRecordedExpectationsAsJava() {
        // given
        String serializedExpectations = new ExpectationToJavaSerializer().serialize(Arrays.asList(
            new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
            new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
                .withBody(new StringBody(serializedExpectations))
        );

        // when
        String actualResponse = mockServerClient.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedExpectations));
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("{" + NEW_LINE +
                    "  \"path\" : \"/some_path\"," + NEW_LINE +
                    "  \"body\" : \"some_request_body\"" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifySingleRequestNoVerificationTimes() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verifySequence")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequests\" : [ {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  } ]" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifyMultipleRequestsNoVerificationTimes() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verifySequence")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequests\" : [ {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }, {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  } ]" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifySingleRequestOnce() {
        // given
        echoServer.withNextResponse(
            response()
                .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            VerificationTimes.once()
        );

        // then
        assertThat(logFilter.retrieveRequests(request()).size(), is(1));
        String result = logFilter.verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verify")
                .withHeaders(
                    new Header("host", "localhost:" + echoServer.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "text/plain; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(new StringBody("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"count\" : 1," + NEW_LINE +
                    "    \"exact\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

}
