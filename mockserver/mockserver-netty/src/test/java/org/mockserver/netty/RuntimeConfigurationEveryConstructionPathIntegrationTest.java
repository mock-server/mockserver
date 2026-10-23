package org.mockserver.netty;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.ReferenceCountUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.metrics.Metrics;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.PortFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

/**
 * A runtime {@code PUT /mockserver/configuration} must take effect on EVERY way a server can be built.
 *
 * <p>The CLI and Docker image start the server with {@code new MockServer(ports)}, which passes no
 * {@link Configuration}. {@code LifeCycle} then built one instance for {@code HttpState} and the event
 * log while {@code MockServer} built a second for the request handlers. The PUT mutated the handlers'
 * copy (so it returned 200 and GET echoed it) while enforcement and capacity resizing read the other:
 * enabling control-plane authentication at runtime left the control plane open. Each check reads its
 * subject through the component that applies it ({@code HttpState}, the action handler, or the request
 * handler an h2c connection gets), and one walks every {@code Configuration} the server holds, so the
 * test goes red if any construction path, or any single component, splits again. Control-plane
 * authentication is now fixed at startup, so the tests also prove a PUT cannot enable it on any path.
 */
@RunWith(Parameterized.class)
public class RuntimeConfigurationEveryConstructionPathIntegrationTest {

    private static final String SENSITIVE_CREDENTIAL = "Bearer runtime-config-split-secret";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RUNTIME_DELAY_MILLIS = 750;
    // a PUT cannot apply this (control-plane mTLS is fixed at startup); the tests prove it is ignored on every path
    private static final String ENABLE_CONTROL_PLANE_MUTUAL_TLS = "{\"controlPlaneTLSMutualAuthenticationRequired\": true, "
        + "\"controlPlaneTLSMutualAuthenticationCAChain\": \"org/mockserver/netty/integration/tls/ca.pem\"}";

    /** A started server, whichever API built it. {@code liveConfiguration} is null where none is exposed. */
    private static final class Started {
        final int port;
        final Configuration liveConfiguration;
        final MockServer mockServer;
        final Runnable stop;
        boolean forwardsThroughAnUpstreamProxy;

        Started(int port, Configuration liveConfiguration, MockServer mockServer, Runnable stop) {
            this.port = port;
            this.liveConfiguration = liveConfiguration;
            this.mockServer = mockServer;
            this.stop = stop;
        }

        static Started of(MockServer mockServer) {
            return new Started(mockServer.getLocalPort(), mockServer.getConfiguration(), mockServer, mockServer::stop);
        }

        static Started of(ClientAndServer clientAndServer) {
            return new Started(clientAndServer.getLocalPort(), null, underlyingMockServer(clientAndServer), clientAndServer::stop);
        }

        Started throughAnUpstreamProxy() {
            forwardsThroughAnUpstreamProxy = true;
            return this;
        }

        private static MockServer underlyingMockServer(ClientAndServer clientAndServer) {
            try {
                Field field = ClientAndServer.class.getDeclaredField("mockServer");
                field.setAccessible(true);
                return (MockServer) field.get(clientAndServer);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> constructionPaths() {
        return Arrays.asList(new Object[][]{
            {"MockServer(Integer...) [CLI, Docker, maven plugin]", (Supplier<Started>) () -> Started.of(new MockServer(0))},
            {"MockServer(remotePort, remoteHost, Integer...) [CLI -proxyRemotePort]", (Supplier<Started>) () -> Started.of(new MockServer(unusedPort(), "localhost", 0))},
            {"MockServer(ProxyConfiguration, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer(httpProxy(), 0)).throughAnUpstreamProxy()},
            {"MockServer(Configuration, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer(configuration(), 0))},
            {"MockServer(null Configuration, List<ProxyConfiguration>, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer((Configuration) null, ImmutableList.<ProxyConfiguration>of(), 0))},
            {"MockServer(Configuration, remotePort, remoteHost, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer(configuration(), unusedPort(), "localhost", 0))},
            {"MockServer(Configuration, ProxyConfiguration, remoteHost, remotePort, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer(configuration(), httpProxy(), "localhost", unusedPort(), 0)).throughAnUpstreamProxy()},
            {"MockServer(null Configuration, List<ProxyConfiguration>, remoteHost, remotePort, Integer...)", (Supplier<Started>) () -> Started.of(new MockServer((Configuration) null, ImmutableList.<ProxyConfiguration>of(), "localhost", unusedPort(), 0))},
            {"ClientAndServer.startClientAndServer(Integer...) [MockServerRule, MockServerExtension]", (Supplier<Started>) () -> Started.of(ClientAndServer.startClientAndServer(0))},
            {"ClientAndServer.startClientAndServer(Configuration, Integer...) [Spring]", (Supplier<Started>) () -> Started.of(ClientAndServer.startClientAndServer(configuration(), 0))},
            {"ClientAndServer.startClientAndServer(remoteHost, remotePort, Integer...)", (Supplier<Started>) () -> Started.of(ClientAndServer.startClientAndServer("localhost", unusedPort(), 0))},
            {"new ClientAndServer(Integer...)", (Supplier<Started>) () -> Started.of(new ClientAndServer(0))},
        });
    }

    private final Supplier<Started> construction;
    private Started server;

    public RuntimeConfigurationEveryConstructionPathIntegrationTest(String name, Supplier<Started> construction) {
        this.construction = construction;
    }

    @Before
    public void startServer() {
        server = construction.get();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop.run();
        }
    }

    @Test
    public void aRuntimePutCannotEnableControlPlaneMutualTls() throws Exception {
        Response put = send("PUT", "/mockserver/configuration", ENABLE_CONTROL_PLANE_MUTUAL_TLS);
        assertThat(put.body, put.statusCode, is(200));

        // control-plane mTLS is fixed at startup: the PUT is ignored, and the value in force is enforced and reported
        assertThat(OBJECT_MAPPER.readTree(put.body).path("controlPlaneTLSMutualAuthenticationRequired").asBoolean(), is(false));
        assertThat(OBJECT_MAPPER.readTree(send("GET", "/mockserver/configuration", "").body).path("controlPlaneTLSMutualAuthenticationRequired").asBoolean(), is(false));
        assertThat("the control plane keeps the authentication it started with",
            send("PUT", "/mockserver/clear", "").statusCode, is(200));
    }

    @Test
    public void shrinkingTheEventLogAtRuntimeMustMoveTheGaugesAndEvict() throws Exception {
        createExpectation(expectation("/log-probe"));
        String body = repeat('x', 500);
        for (int i = 0; i < 40; i++) {
            assertThat(send("POST", "/log-probe", body).statusCode, is(200));
        }
        assertThat("baseline: all 40 probes are retained under the startup event-log limits",
            awaitRecordedRequestCount(count -> count >= 40), greaterThanOrEqualTo(40));
        assertThat("baseline: the retained-entries cap gauge is above the value about to be applied",
            Metrics.getEventLogRingStats().maxRetainedEntries > 10, is(true));

        Response put = send("PUT", "/mockserver/configuration", "{\"maxEventLogSizeInBytes\": 5000, \"maxLogEntries\": 10}");
        assertThat(put.body, put.statusCode, is(200));

        Metrics.RingStats stats = Metrics.getEventLogRingStats();
        assertThat("mock_server_event_log_max_retained_entries must report the runtime maxLogEntries",
            stats.maxRetainedEntries, is(10L));
        assertThat("mock_server_event_log_max_retained_bytes must report the runtime maxEventLogSizeInBytes",
            stats.maxRetainedBytes, is(5000L));
        // the in-flight cap is derived: the larger of maxEventLogSizeInBytes and a heap-derived floor
        assertThat("mock_server_event_log_max_in_flight_bytes must report the cap derived from the runtime maxEventLogSizeInBytes",
            stats.maxInFlightBytes, is(configuration().maxEventLogSizeInBytes(5000L).maxEventLogInFlightBytes()));
        assertThat("the event log must have evicted down to the runtime maxLogEntries",
            stats.retainedEntries, lessThanOrEqualTo(10L));
        assertThat("the event log must have evicted down to the runtime maxEventLogSizeInBytes",
            stats.retainedBytes, lessThanOrEqualTo(5000L));
        assertThat("retrieval must see the evicted log, not the startup-sized one",
            recordedRequestCount(), lessThanOrEqualTo(10));
    }

    @Test
    public void enablingSecretRedactionAtRuntimeMustMaskCredentialsInTheRetrievedLog() throws Exception {
        createExpectation(expectation("/redaction-probe"));

        assertThat(send("POST", "/redaction-probe", "{}", SENSITIVE_CREDENTIAL).statusCode, is(200));
        assertThat("baseline: with redactSecretsInLog off the credential is retrievable verbatim",
            retrieveRecordedRequests(), containsString(SENSITIVE_CREDENTIAL));

        Response put = send("PUT", "/mockserver/configuration", "{\"redactSecretsInLog\": true}");
        assertThat(put.body, put.statusCode, is(200));

        String retrieved = retrieveRecordedRequests();
        assertThat("redactSecretsInLog=true set at runtime must mask the recorded credential",
            retrieved, not(containsString(SENSITIVE_CREDENTIAL)));
        assertThat(retrieved, containsString("***REDACTED***"));
    }

    @Test
    public void theConfigurationTheServerExposesMustBeTheOneTheControlPlaneServes() throws Exception {
        assumeTrue("this construction API does not expose the server's Configuration",
            server.liveConfiguration != null);

        server.liveConfiguration.maxExpectations(4321);

        JsonNode served = OBJECT_MAPPER.readTree(send("GET", "/mockserver/configuration", "").body);
        assertThat("GET /mockserver/configuration must serve the same instance HttpState runs on",
            served.path("maxExpectations").asInt(), is(4321));
    }

    @Test
    public void aRuntimePutOverH2cMustReachTheConfigurationTheServerRuns() throws Exception {
        // Every call is a stream on ONE cleartext HTTP/2 connection opened before the change. The PUT is
        // served by the request handler the HTTP/2 multiplex child initializer built for this connection,
        // so it must write the instance the rest of the server reads.
        try (H2cConnection h2c = new H2cConnection(server.port)) {
            H2cResponse put = h2c.send("PUT", "/mockserver/configuration", "{\"maxExpectations\": 4321}");
            assertThat(put.body, put.status, is("200"));

            H2cResponse ignored = h2c.send("PUT", "/mockserver/configuration", ENABLE_CONTROL_PLANE_MUTUAL_TLS);
            assertThat(ignored.body, ignored.status, is("200"));
            assertThat("control-plane mTLS is fixed at startup, so the next call on the same connection is still served",
                h2c.send("PUT", "/mockserver/clear", "").status, is("200"));
            assertThat(h2c.isOpen(), is(true));
        }
        JsonNode served = OBJECT_MAPPER.readTree(send("GET", "/mockserver/configuration", "").body);
        assertThat("a new HTTP/1.1 connection must see the value the h2c PUT applied", served.path("maxExpectations").asInt(), is(4321));
        if (server.liveConfiguration != null) {
            assertThat(server.liveConfiguration.maxExpectations(), is(4321));
        }
    }

    @Test
    public void aRuntimeGlobalResponseDelayMustDelayAMockedResponse() throws Exception {
        // globalResponseDelayMillis is read by HttpActionHandler when it dispatches a mocked response
        createExpectation(expectation("/delay-probe"));

        Response put = send("PUT", "/mockserver/configuration", "{\"globalResponseDelayMillis\": " + RUNTIME_DELAY_MILLIS + "}");
        assertThat(put.body, put.statusCode, is(200));

        long start = System.nanoTime();
        assertThat(send("GET", "/delay-probe", "").statusCode, is(200));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat("the action handler must apply the runtime globalResponseDelayMillis",
            elapsedMillis, greaterThanOrEqualTo((long) RUNTIME_DELAY_MILLIS));
    }

    @Test
    public void aRuntimeProxyRemoteHostMustForwardAnUnmatchedRequest() throws Exception {
        assumeTrue("a remote given at construction is bound to every connection and takes precedence",
            server.mockServer.getRemoteAddress() == null);
        assumeTrue("forwarding goes through the (unreachable) upstream proxy this shape configures",
            !server.forwardsThroughAnUpstreamProxy);
        MockServer upstream = new MockServer(configuration(), 0);
        try {
            Response upstreamExpectation = send(upstream.getLocalPort(), "PUT", "/mockserver/expectation",
                "{\"httpRequest\": {\"path\": \"/forward-probe\"}, \"httpResponse\": {\"statusCode\": 200, \"body\": \"from-upstream\"}}", null);
            assertThat(upstreamExpectation.body, upstreamExpectation.statusCode, is(201));
            assertThat("baseline: an unmatched request is not forwarded", send("GET", "/forward-probe", "").statusCode, is(404));

            Response put = send("PUT", "/mockserver/configuration",
                "{\"proxyRemoteHost\": \"127.0.0.1\", \"proxyRemotePort\": " + upstream.getLocalPort() + "}");
            assertThat(put.body, put.statusCode, is(200));

            Response forwarded = send("GET", "/forward-probe", "");
            assertThat("the action handler must forward an unmatched request to the runtime proxyRemoteHost",
                forwarded.body, is("from-upstream"));
        } finally {
            upstream.stop();
        }
    }

    @Test
    public void everyConfigurationHoldingComponentMustBeTheServersInstance() throws Exception {
        ServerConfigurationIdentity identity = ServerConfigurationIdentity.walk(server.mockServer);

        assertThat("components holding a Configuration other than the server's would never see a runtime PUT",
            identity.divergent(), empty());
        for (String component : new String[]{
            "HttpState.configuration",
            "Scheduler.configuration",
            "HttpActionHandler.configuration",
            "NettyHttpClient.configuration",
            "MockServerUnificationInitializer.configuration",
            "InboundConnectionLimiter.configuration",
            "PortUnificationHandler.configuration",
            "HttpRequestHandler.configuration",
            "Http2MultiplexChildInitializer.configuration",
        }) {
            assertThat("the walk must reach " + component, identity.matchedHolders(), hasItem(component));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static ProxyConfiguration httpProxy() {
        return proxyConfiguration(ProxyConfiguration.Type.HTTP, "127.0.0.1:" + unusedPort());
    }

    private static int unusedPort() {
        return PortFactory.findFreePort();
    }

    private static String expectation(String path) {
        return "{\"httpRequest\": {\"path\": \"" + path + "\"}, \"httpResponse\": {\"statusCode\": 200, \"body\": \"ok\"}}";
    }

    private void createExpectation(String expectationJson) throws IOException {
        Response response = send("PUT", "/mockserver/expectation", expectationJson);
        assertThat(response.body, response.statusCode, is(201));
    }

    private String retrieveRecordedRequests() throws IOException {
        return send("PUT", "/mockserver/retrieve?type=REQUESTS&format=JSON", "").body;
    }

    private int recordedRequestCount() throws IOException {
        JsonNode requests = OBJECT_MAPPER.readTree(retrieveRecordedRequests());
        return requests.isArray() ? requests.size() : 0;
    }

    /** The event log is fed asynchronously through the ring, so wait for the probes to be processed. */
    private int awaitRecordedRequestCount(IntPredicate condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        int count = recordedRequestCount();
        while (!condition.test(count) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            count = recordedRequestCount();
        }
        return count;
    }

    private Response send(String method, String path, String body) throws IOException {
        return send(method, path, body, null);
    }

    private Response send(String method, String path, String body, String authorization) throws IOException {
        return send(server.port, method, path, body, authorization);
    }

    private static Response send(int port, String method, String path, String body, String authorization) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            if (authorization != null) {
                connection.setRequestProperty("Authorization", authorization);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(statusCode, stream == null ? "" : new String(drain(stream), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    /** A cleartext HTTP/2 connection with prior knowledge (no upgrade); each request opens a stream on it. */
    private static final class H2cConnection implements AutoCloseable {
        private final NioEventLoopGroup group = new NioEventLoopGroup(1);
        private final Channel parent;
        private final int port;

        H2cConnection(int port) throws InterruptedException {
            this.port = port;
            this.parent = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                    }
                })
                .connect("127.0.0.1", port).sync().channel();
        }

        H2cResponse send(String method, String path, String body) throws Exception {
            CompletableFuture<H2cResponse> future = new CompletableFuture<>();
            H2cResponse response = new H2cResponse();
            StringBuilder collected = new StringBuilder();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(parent)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        try {
                            if (msg instanceof Http2HeadersFrame) {
                                Http2HeadersFrame headers = (Http2HeadersFrame) msg;
                                if (headers.headers().status() != null) {
                                    response.status = headers.headers().status().toString();
                                }
                                if (headers.isEndStream()) {
                                    complete();
                                }
                            } else if (msg instanceof Http2DataFrame) {
                                collected.append(((Http2DataFrame) msg).content().toString(StandardCharsets.UTF_8));
                                if (((Http2DataFrame) msg).isEndStream()) {
                                    complete();
                                }
                            }
                        } finally {
                            ReferenceCountUtil.release(msg);
                        }
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        complete();
                    }

                    private void complete() {
                        response.body = collected.toString();
                        future.complete(response);
                    }
                })
                .open().sync().getNow();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            Http2Headers headers = new DefaultHttp2Headers()
                .method(method)
                .scheme(HttpScheme.HTTP.name())
                .authority("127.0.0.1:" + port)
                .path(path);
            stream.writeAndFlush(new DefaultHttp2HeadersFrame(headers, bytes.length == 0));
            if (bytes.length > 0) {
                stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(bytes), true));
            }
            return future.get(20, TimeUnit.SECONDS);
        }

        boolean isOpen() {
            return parent.isActive();
        }

        @Override
        public void close() {
            parent.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    private static final class H2cResponse {
        String status;
        String body = "";
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static final class Response {
        final int statusCode;
        final String body;

        Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
