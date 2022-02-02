package org.mockserver.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundInvoker;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.LogEventRequestAndResponseSerializer;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.Scheme.HTTPS;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;

/**
 * @author jamesdbloom
 */
public class MockServerClientIntegrationTest {

    public static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(MockServerClientIntegrationTest.class);
    private static MockServerClient mockServerClientOne;
    private static MockServerClient mockServerClientTwo;
    private static EchoServer echoServerOne;
    private static EchoServer echoServerTwo;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void createClient() {
        echoServerOne = new EchoServer(false);
        echoServerTwo = new EchoServer(false);
        mockServerClientOne = new MockServerClient("localhost", echoServerOne.getPort());
        mockServerClientTwo = new MockServerClient("localhost", echoServerTwo.getPort());
    }

    @After
    public void stopClient() {
        stopQuietly(echoServerOne);
        stopQuietly(echoServerTwo);
        stopQuietly(mockServerClientOne);
    }

    private List<RequestDefinition> retrieveRequests(HttpRequest httpRequest) {
        CompletableFuture<List<RequestDefinition>> result = new CompletableFuture<>();
        echoServerOne.mockServerEventLog().retrieveRequests(httpRequest, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return new ArrayList<>();
        }
    }

    public String verify(Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        echoServerOne.mockServerEventLog().verify(verification, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    public String verify(VerificationSequence verificationSequence) {
        CompletableFuture<String> result = new CompletableFuture<>();
        echoServerOne.mockServerEventLog().verify(verificationSequence, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenRespond(
                response()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseTemplate() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenRespond(
                template(HttpTemplate.TemplateType.VELOCITY)
                    .withTemplate("some_response_template")
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseClassCallback() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenRespond(
                callback()
                    .withCallbackClass("some_class")
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithResponseObjectCallback() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));
        echoServerOne.getRegisteredClients().clear();

        // when
        mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                once()
            )
            .respond(httpRequest -> response());

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpResponseObjectCallback\" : {" + NEW_LINE +
                    "    \"clientId\" : \"" + echoServerOne.getRegisteredClients().get(0) + "\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"remainingTimes\" : 1" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldReconnectWebSocketIfClosed() throws InterruptedException {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));
        echoServerOne.getRegisteredClients().clear();
        mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                once()
            )
            .respond(httpRequest -> response());

        // when
        assertThat(echoServerOne.getWebsocketChannels().size(), is(1));
        Channel initialChannel = echoServerOne.getWebsocketChannels().get(0);
        new ArrayList<>(echoServerOne.getWebsocketChannels()).forEach(ChannelOutboundInvoker::close);

        SECONDS.sleep(1);

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        assertThat(echoServerOne.getWebsocketChannels().size(), is(1));
        assertThat(echoServerOne.getWebsocketChannels().get(0), IsNot.not(sameInstance(initialChannel)));
    }

    @Test
    public void shouldCloseWebsocketAfterStop() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));
        echoServerOne.getRegisteredClients().clear();

        // when
        mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                once()
            )
            .respond(httpRequest -> response());

        // then
        assertThat(echoServerOne.getRegisteredClients().size(), is(1));

        // when
        mockServerClientOne.stop();

        // then
        assertThat(echoServerOne.getRegisteredClients().size(), is(0));
    }

    @Test
    public void shouldCloseWebsocketAfterStopWithMultipleClients() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));
        echoServerOne.getRegisteredClients().clear();
        echoServerTwo.withNextResponse(response().withStatusCode(201));
        echoServerTwo.getRegisteredClients().clear();

        // when
        mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                once()
            )
            .respond(httpRequest -> response());
        mockServerClientTwo
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                once()
            )
            .respond(httpRequest -> response());

        // then
        assertThat(echoServerOne.getRegisteredClients().size(), is(1));
        assertThat(echoServerTwo.getRegisteredClients().size(), is(1));

        // when
        mockServerClientOne.stop();

        // then
        assertThat(echoServerOne.getRegisteredClients().size(), is(0));
        assertThat(echoServerTwo.getRegisteredClients().size(), is(1));
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenForward(
                forward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HTTPS)
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardTemplate() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenForward(
                template(HttpTemplate.TemplateType.VELOCITY)
                    .withTemplate("some_response_template")
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardClassCallback() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenForward(
                callback()
                    .withCallbackClass("some_class")
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForwardObjectCallback() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(httpRequest -> request());

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpForwardObjectCallback\" : {" + NEW_LINE +
                    "    \"clientId\" : \"" + echoServerOne.getRegisteredClients().get(0) + "\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithOverrideForwardedRequest() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
                "{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/some_path\"," + NEW_LINE +
                "    \"body\" : \"some_request_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
                "    \"httpRequest\" : {" + NEW_LINE +
                "      \"headers\" : {" + NEW_LINE +
                "        \"host\" : [ \"localhost:" + echoServerOne.getPort() + "\" ]" + NEW_LINE +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
            .when(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("host", "localhost:" + echoServerOne.getPort())
                        .withBody("some_override_body")
                )
            );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
                    "{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/some_path\"," + NEW_LINE +
                    "    \"body\" : \"some_request_body\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"httpOverrideForwardedRequest\" : {" + NEW_LINE +
                    "    \"requestOverride\" : {" + NEW_LINE +
                    "      \"headers\" : {" + NEW_LINE +
                    "        \"host\" : [ \"localhost:" + echoServerOne.getPort() + "\" ]" + NEW_LINE +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithError() {
        // given
        echoServerOne.withNextResponse(response()
            .withStatusCode(201)
            .withBody(json("" +
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
                "}", APPLICATION_JSON_UTF_8))
        );

        // when
        Expectation[] upsertedExpectations = mockServerClientOne
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
        assertThat(upsertedExpectations[0], is(new Expectation(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")))
            .thenError(
                error()
                    .withDropConnection(true)
                    .withResponseBytes("silly_bytes".getBytes(UTF_8))
            )
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendExpectationRequestWithExactTimes() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne
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
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/expectation")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
                .withBody(json("" +
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
                    "}", APPLICATION_JSON_UTF_8))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendStopRequest() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.stop();

        // then
        String result = verify(verificationSequence().withRequests(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/stop")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
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
                    new Header("host", "localhost:" + echoServerOne.getPort()),
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
        echoServerOne.withNextResponse(response().withStatusCode(200));

        // when
        boolean hasStarted = mockServerClientOne.hasStarted();
        boolean hasStopped = mockServerClientOne.hasStopped();

        // then
        assertThat(hasStopped, is(false));
        assertThat(hasStarted, is(true));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/status")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
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
        try {
            // given
            int numberOfRetries = 20;
            HttpResponse[] httpResponses = new HttpResponse[numberOfRetries];
            Arrays.fill(httpResponses, response().withStatusCode(404));
            echoServerOne.withNextResponse(httpResponses);

            // when
            boolean hasStopped = mockServerClientOne.hasStopped();
            boolean hasStarted = mockServerClientOne.hasStarted();

            // then
            assertThat(hasStopped, is(true));
            assertThat(hasStarted, is(false));
            String result = verify(verification().withRequest(
                request()
                    .withMethod("PUT")
                    .withPath("/mockserver/status")
                    .withHeaders(
                        new Header("host", "localhost:" + echoServerOne.getPort()),
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
        } finally {
            echoServerOne.clear();
        }
    }

    @Test
    public void shouldSendResetRequest() {
        // given
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.reset();

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/reset")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne
            .clear(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne
            .clear(
                request()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                ClearType.LOG
            );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.clear((RequestDefinition) null);

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new HttpRequestSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
                    request("/some_request_one"),
                    request("/some_request_two")
                ))))
        );

        // when
        RequestDefinition[] actualResponse = mockServerClientOne.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new HttpRequestSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
                    request("/some_request_one"),
                    request("/some_request_two")
                ))))
        );

        // when
        RequestDefinition[] actualResponse = mockServerClientOne.retrieveRecordedRequests(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        String serializedRequests = new HttpRequestSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
            request("/some_request_one"),
            request("/some_request_two")
        ));
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializedRequests))
        );

        // when
        String recordedResponse = mockServerClientOne.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(recordedResponse, is(serializedRequests));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializedRequest))
        );

        // when
        String actualResponse = mockServerClientOne.retrieveRecordedRequests(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedRequest));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
    public void shouldRetrieveRequestAndResponses() {
        // given
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new LogEventRequestAndResponseSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
                    new LogEventRequestAndResponse()
                        .withHttpRequest(request("/some_request_one"))
                        .withHttpResponse(response("some_body_one")),
                    new LogEventRequestAndResponse()
                        .withHttpRequest(request("/some_request_two"))
                        .withHttpResponse(response("some_body_two"))
                ))))
        );

        // when
        LogEventRequestAndResponse[] actualResponse = mockServerClientOne.retrieveRecordedRequestsAndResponses(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_one"))
                .withHttpResponse(response("some_body_one")),
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_two"))
                .withHttpResponse(response("some_body_two"))
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
    public void shouldRetrieveRequestAndResponsesWithNullRequest() {
        // given
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new LogEventRequestAndResponseSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
                    new LogEventRequestAndResponse()
                        .withHttpRequest(request("/some_request_one"))
                        .withHttpResponse(response("some_body_one")),
                    new LogEventRequestAndResponse()
                        .withHttpRequest(request("/some_request_two"))
                        .withHttpResponse(response("some_body_two"))
                ))))
        );

        // when
        LogEventRequestAndResponse[] actualResponse = mockServerClientOne.retrieveRecordedRequestsAndResponses(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_one"))
                .withHttpResponse(response("some_body_one")),
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_two"))
                .withHttpResponse(response("some_body_two"))
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
                )
                .withSecure(false)
                .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequestAndResponsesAsJson() {
        // given
        String serializedRequests = new LogEventRequestAndResponseSerializer(MOCK_SERVER_LOGGER).serialize(Arrays.asList(
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_one"))
                .withHttpResponse(response("some_body_one")),
            new LogEventRequestAndResponse()
                .withHttpRequest(request("/some_request_two"))
                .withHttpResponse(response("some_body_two"))
        ));
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializedRequests))
        );

        // when
        String recordedResponse = mockServerClientOne.retrieveRecordedRequestsAndResponses(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(recordedResponse, is(serializedRequests));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
                    new Expectation(request("/some_request_one")).thenRespond(response()),
                    new Expectation(request("/some_request_two")).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClientOne.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
                    new Expectation(request("/some_request_one")).thenRespond(response()),
                    new Expectation(request("/some_request_two")).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClientOne.retrieveActiveExpectations(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        String serializeExpectations = new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        );
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializeExpectations))
        );

        // when
        String actualResponse = mockServerClientOne.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(actualResponse, is(serializeExpectations));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializedExpectations))
        );

        // when
        String actualResponse = mockServerClientOne.retrieveActiveExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedExpectations));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
                    new Expectation(request("/some_request_one")).thenRespond(response()),
                    new Expectation(request("/some_request_two")).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClientOne.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
                    new Expectation(request("/some_request_one")).thenRespond(response()),
                    new Expectation(request("/some_request_two")).thenRespond(response())
                )))
        );

        // when
        Expectation[] actualResponse = mockServerClientOne.retrieveRecordedExpectations(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("content-length", "0"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        String serializeExpectations = new ExpectationSerializer(MOCK_SERVER_LOGGER).serialize(
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        );
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializeExpectations))
        );

        // when
        String actualResponse = mockServerClientOne.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JSON
        );

        // then
        assertThat(actualResponse, is(serializeExpectations));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
            new Expectation(request("/some_request_one")).thenRespond(response()),
            new Expectation(request("/some_request_two")).thenRespond(response())
        ));
        echoServerOne.withNextResponse(
            response()
                .withStatusCode(201)
                .withContentType(APPLICATION_JSON)
                .withBody(new StringBody(serializedExpectations))
        );

        // when
        String actualResponse = mockServerClientOne.retrieveRecordedExpectations(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            Format.JAVA
        );

        // then
        assertThat(actualResponse, is(serializedExpectations));
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JAVA.name())
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verifySequence")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verifySequence")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
        echoServerOne.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClientOne.verify(
            request()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body")),
            VerificationTimes.once()
        );

        // then
        assertThat(retrieveRequests(request()).size(), is(1));
        String result = verify(verification().withRequest(
            request()
                .withMethod("PUT")
                .withPath("/mockserver/verify")
                .withHeaders(
                    new Header("host", "localhost:" + echoServerOne.getPort()),
                    new Header("accept-encoding", "gzip,deflate"),
                    new Header("connection", "keep-alive"),
                    new Header("content-type", "application/json; charset=utf-8")
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
                    "    \"atLeast\" : 1," + NEW_LINE +
                    "    \"atMost\" : 1" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

}
