package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.Action;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.integration.PipelinedHttp1Client;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.DnsResponse.dnsResponse;
import static org.mockserver.model.GrpcBidiResponse.grpcBidiResponse;
import static org.mockserver.model.GrpcStreamResponse.grpcStreamResponse;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpForwardValidateAction.forwardValidate;
import static org.mockserver.model.HttpForwardWithFallback.forwardWithFallback;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpSseResponse.sseResponse;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.HttpWebSocketResponse.webSocketResponse;
import static org.mockserver.model.SseEvent.sseEvent;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Every way an HTTP/1.1 exchange can end lets the request pipelined behind it through: for every {@link Action} type
 * and every control-plane route, a request sent right behind it on the same connection is answered, or the connection
 * closes, within {@value #END_MILLIS} ms. An exchange that never signals its end would leave the request behind it
 * held until the connection's idle timeout.
 */
public class Http1PipelinedExchangeEndCoverageIntegrationTest {

    private static final int END_MILLIS = 5_000;
    private static final String PROBE_BODY = "probe answered";

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static MockServer upstreamServer;
    private static MockServerClient upstreamClient;
    private static int port;
    private static int upstreamPort;

    private enum End {ANSWERED, CLOSED, ANSWERED_OR_CLOSED}

    @BeforeClass
    public static void startServers() {
        upstreamServer = new MockServer(configuration().logLevel("WARN"), 0);
        upstreamPort = upstreamServer.getLocalPort();
        upstreamClient = new MockServerClient("127.0.0.1", upstreamPort);
        mockServer = new MockServer(configuration()
            .logLevel("WARN")
            .proxySetup(false)
            .attemptToProxyIfNoMatchingExpectation(false), 0);
        port = mockServer.getLocalPort();
        mockServerClient = new MockServerClient("127.0.0.1", port);
    }

    @AfterClass
    public static void stopServers() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        stopQuietly(upstreamClient);
        stopQuietly(upstreamServer);
    }

    @Before
    public void resetExpectations() {
        mockServerClient.reset();
        upstreamClient.reset();
        upstreamClient.when(request()).respond(response("upstream"));
    }

    @Test
    public void shouldEndEveryActionTypeSoThatTheRequestPipelinedBehindItIsAnswered() throws Exception {
        Set<Action.Type> covered = EnumSet.noneOf(Action.Type.class);
        List<String> failures = new ArrayList<>();

        check(failures, covered, Action.Type.RESPONSE, End.ANSWERED, client -> client.when(first()).respond(response("first")));
        check(failures, covered, Action.Type.RESPONSE_TEMPLATE, End.ANSWERED, client -> client.when(first()).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"templated\" }")));
        check(failures, covered, Action.Type.RESPONSE_CLASS_CALLBACK, End.ANSWERED, client -> client.when(first()).respond(callback().withCallbackClass(Http1PipelinedResponseOrderIntegrationTest.SlowClassCallback.class)));
        check(failures, covered, Action.Type.RESPONSE_OBJECT_CALLBACK, End.ANSWERED, client -> client.when(first()).respond(request -> response("object callback")));
        check(failures, covered, Action.Type.FORWARD, End.ANSWERED, client -> client.when(first()).forward(forward().withHost("127.0.0.1").withPort(upstreamPort)));
        check(failures, covered, Action.Type.FORWARD_TEMPLATE, End.ANSWERED, client -> client.when(first()).forward(template(VELOCITY,
            "{ \"path\": \"/upstream\", \"headers\": { \"Host\": [ \"127.0.0.1:" + upstreamPort + "\" ] } }")));
        check(failures, covered, Action.Type.FORWARD_CLASS_CALLBACK, End.ANSWERED, client -> client.when(first()).forward(callback().withCallbackClass(ForwardToUpstream.class)));
        check(failures, covered, Action.Type.FORWARD_OBJECT_CALLBACK, End.ANSWERED, client -> client.when(first()).forward(request -> request.withHeader("Host", "127.0.0.1:" + upstreamPort)));
        check(failures, covered, Action.Type.FORWARD_REPLACE, End.ANSWERED, client -> client.when(first()).forward(forwardOverriddenRequest(request().withHeader("Host", "127.0.0.1:" + upstreamPort))));
        check(failures, covered, Action.Type.FORWARD_VALIDATE, End.ANSWERED, client -> client.upsert(new Expectation(first()).thenForwardValidate(forwardValidate()
            .withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json")
            .withHost("127.0.0.1")
            .withPort(upstreamPort))));
        check(failures, covered, Action.Type.FORWARD_WITH_FALLBACK, End.ANSWERED, client -> client.when(first()).forwardWithFallback(forwardWithFallback()
            .withForward(forward().withHost("127.0.0.1").withPort(upstreamPort))
            .withFallback(response("fallback"))));
        check(failures, covered, Action.Type.SSE_RESPONSE, End.ANSWERED, client -> client.when(first()).respondWithSse(sseResponse()
            .withCloseConnection(false)
            .withEvents(sseEvent().withData("one"), sseEvent().withData("two").withDelay(TimeUnit.MILLISECONDS, 100))));
        // the WebSocket upgrade itself ends HTTP on the connection; a refused handshake ends with a response
        check(failures, covered, Action.Type.WEBSOCKET_RESPONSE, End.ANSWERED, client -> client.when(first()).respondWithWebSocket(webSocketResponse()), "Upgrade: websocket", "Connection: Upgrade", "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==", "Sec-WebSocket-Version: 99");
        check(failures, covered, Action.Type.GRPC_STREAM_RESPONSE, End.ANSWERED_OR_CLOSED, client -> client.when(first()).respondWithGrpcStream(grpcStreamResponse()));
        check(failures, covered, Action.Type.GRPC_BIDI_RESPONSE, End.ANSWERED_OR_CLOSED, client -> client.when(first()).respondWithGrpcBidi(grpcBidiResponse()));
        // a binary or DNS action cannot answer HTTP, so the request it matches gets a 501
        check(failures, covered, Action.Type.BINARY_RESPONSE, End.ANSWERED, client -> client.when(first()).respondWithBinary(binaryResponse("binary".getBytes(StandardCharsets.UTF_8))));
        check(failures, covered, Action.Type.DNS_RESPONSE, End.ANSWERED, client -> client.when(first()).respondWithDns(dnsResponse()));
        check(failures, covered, Action.Type.LLM_RESPONSE, End.ANSWERED, client -> client.when(first()).respondWithLlm(llmResponse()
            .withProvider(Provider.OPENAI)
            .withModel("gpt-test")
            .withCompletion(completion().withText("hello"))));
        check(failures, covered, Action.Type.ERROR, End.ANSWERED, client -> client.when(first()).error(error()));

        assertThat("every action type is covered", EnumSet.complementOf(EnumSet.copyOf(covered)), is(EnumSet.noneOf(Action.Type.class)));
        assertThat(failures, is(empty()));
    }

    @Test
    public void shouldEndEveryWayAnErrorEndsSoThatTheRequestPipelinedBehindItIsAnsweredOrTheConnectionCloses() throws Exception {
        List<String> failures = new ArrayList<>();
        Set<Action.Type> covered = EnumSet.noneOf(Action.Type.class);

        check(failures, covered, Action.Type.ERROR, End.ANSWERED, client -> client.when(first()).error(error().withResponseBytes("HTTP/1.1 200 OK\r\ncontent-length: 3\r\n\r\nraw".getBytes(StandardCharsets.US_ASCII))));
        check(failures, covered, Action.Type.ERROR, End.CLOSED, client -> client.when(first()).error(error().withDropConnection(true)));
        check(failures, covered, Action.Type.ERROR, End.CLOSED, client -> client.when(first()).error(error().withResetConnection(true)));
        check(failures, covered, Action.Type.ERROR, End.CLOSED, client -> client.when(first()).error(error().withResponseBytes("HTTP/1.1 200 OK\r\ncontent-length: 3\r\n\r\nraw".getBytes(StandardCharsets.US_ASCII)).withDropConnection(true)));
        check(failures, covered, Action.Type.RESPONSE, End.ANSWERED, client -> client.when(first()).respond(response().withStatusCode(103)));
        check(failures, covered, Action.Type.RESPONSE, End.CLOSED, client -> client.when(first()).respond(response("closes").withConnectionOptions(org.mockserver.model.ConnectionOptions.connectionOptions().withCloseSocket(true))));
        check(failures, covered, Action.Type.SSE_RESPONSE, End.CLOSED, client -> client.when(first()).respondWithSse(sseResponse().withCloseConnection(true).withEvents(sseEvent().withData("one"))));

        assertThat(failures, is(empty()));
    }

    @Test
    public void shouldAnswerAnHttpRequestMatchedByABinaryOrDnsActionWith501AndThenTheRequestPipelinedBehindIt() throws Exception {
        mockServerClient.when(request().withPath("/binary")).respondWithBinary(binaryResponse("binary".getBytes(StandardCharsets.UTF_8)));
        mockServerClient.when(request().withPath("/dns")).respondWithDns(dnsResponse());
        mockServerClient.when(request().withPath("/probe")).respond(response(PROBE_BODY));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequest("GET", "/binary"), rawRequest("GET", "/dns"), rawRequest("GET", "/probe"));

            PipelinedHttp1Client.Response binary = client.readResponse();
            PipelinedHttp1Client.Response dns = client.readResponse();
            assertThat(binary.status, is(501));
            assertThat(binary.body(), is("the matched expectation's BINARY_RESPONSE action cannot answer an HTTP request"));
            assertThat(dns.status, is(501));
            assertThat(dns.body(), is("the matched expectation's DNS_RESPONSE action cannot answer an HTTP request"));
            assertThat(client.readResponse().body(), is(PROBE_BODY));
        }
    }

    /**
     * Every control-plane route in MockServer's OpenAPI description, sent with an empty body.
     */
    @Test
    public void shouldEndEveryControlPlaneRouteSoThatTheRequestPipelinedBehindItIsAnsweredOrTheConnectionCloses() throws Exception {
        List<String> failures = new ArrayList<>();
        int routes = 0;
        for (String[] route : controlPlaneRoutes()) {
            String method = route[0];
            String path = route[1];
            if (path.equals("/mockserver/stop")) {
                continue;
            }
            routes++;
            End end = endOf(rawRequest(method, path), END_MILLIS);
            if (end == null) {
                failures.add(method + " " + path + ": neither answered nor closed within " + END_MILLIS + "ms");
            }
            if (path.equals("/mockserver/preemption") && !method.equals("DELETE")) {
                endOf(rawRequest("DELETE", "/mockserver/preemption"), END_MILLIS);
            }
            resetExpectations();
        }
        assertThat("the routes were found", routes > 50, is(true));
        assertThat(failures, is(empty()));
    }

    private static HttpRequest first() {
        return request().withPath("/first");
    }

    private void check(List<String> failures, Set<Action.Type> covered, Action.Type type, End expected, Consumer<MockServerClient> expectation, String... headers) throws Exception {
        covered.add(type);
        resetExpectations();
        expectation.accept(mockServerClient);
        End end = endOf(rawRequest("GET", "/first", headers), END_MILLIS);
        if (end == null) {
            failures.add(type + ": neither answered nor closed within " + END_MILLIS + "ms");
        } else if (expected != End.ANSWERED_OR_CLOSED && end != expected) {
            failures.add(type + ": " + end + ", expected " + expected);
        }
    }

    /**
     * Sends {@code first} with a probe request pipelined behind it, and reports whether the probe was answered (its
     * response, or a second response when {@code first} removed the probe's expectation) or the connection closed, or
     * null if neither happened in time. {@code first} may end without a response, as an empty {@code error()} does.
     */
    private End endOf(String first, int millis) throws IOException {
        mockServerClient.when(request().withPath("/probe")).respond(response(PROBE_BODY));
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(first, rawRequest("GET", "/probe"));
            long deadline = System.currentTimeMillis() + millis;
            for (int responses = 0; responses < 2; responses++) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                client.withReadTimeout((int) remaining);
                try {
                    if (client.readResponse().body().equals(PROBE_BODY)) {
                        return End.ANSWERED;
                    }
                } catch (SocketTimeoutException quiet) {
                    return null;
                } catch (IOException closed) {
                    return End.CLOSED;
                }
            }
            return End.ANSWERED;
        }
    }

    private static String rawRequest(String method, String path, String... headers) {
        StringBuilder request = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\nHost: 127.0.0.1:").append(port).append("\r\n");
        for (String header : headers) {
            request.append(header).append("\r\n");
        }
        if (!method.equals("GET")) {
            request.append("Content-Length: 0\r\n");
        }
        return request.append("\r\n").toString();
    }

    private static List<String[]> controlPlaneRoutes() throws IOException {
        Pattern pathLine = Pattern.compile("^  (/\\S*):\\s*$");
        Pattern methodLine = Pattern.compile("^    (get|put|post|delete|patch):\\s*$");
        List<String[]> routes = new ArrayList<>();
        try (InputStream spec = Http1PipelinedExchangeEndCoverageIntegrationTest.class.getClassLoader().getResourceAsStream("org/mockserver/openapi/mock-server-openapi-embedded-model.yaml")) {
            String yaml = new String(spec.readAllBytes(), StandardCharsets.UTF_8);
            String paths = yaml.substring(yaml.indexOf("\npaths:\n"), yaml.indexOf("\ncomponents:\n"));
            String path = null;
            for (String line : paths.split("\n")) {
                Matcher pathMatch = pathLine.matcher(line);
                Matcher methodMatch = methodLine.matcher(line);
                if (pathMatch.matches()) {
                    path = pathMatch.group(1).replaceAll("\\{[^}]*}", "unknown");
                } else if (methodMatch.matches() && path != null) {
                    routes.add(new String[]{methodMatch.group(1).toUpperCase(), path});
                }
            }
        }
        return routes;
    }

    public static class ForwardToUpstream implements ExpectationForwardCallback {
        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return httpRequest.withHeader("Host", "127.0.0.1:" + upstreamPort);
        }
    }
}
