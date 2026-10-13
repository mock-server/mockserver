package org.mockserver.netty.integration.proxy.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpForward;
import org.mockserver.model.ProxyPassMapping;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * The forward client reads an upstream's response headers and trailers up to {@code maxHeaderSize}, over HTTP/1.1,
 * over HTTP/2 and for the response of an upstream proxy to {@code CONNECT}; a larger one fails that forward with a
 * {@code 502} that says why, and one WARN. A client (a raw socket) sends plain HTTP/1.1 to MockServer, which forwards
 * to upstreams on 127.0.0.1: one that writes raw HTTP/1.1, and TLS ones that negotiate HTTP/2.
 */
public class ForwardHeaderLimitIntegrationTest {

    // above the 8,192 bytes Netty allows a response's headers when it is not told otherwise
    private static final int LIMIT = 32 * 1024;
    private static final int DEFAULT_LIMIT = 256 * 1024;
    private static final int UPSTREAM_REQUEST_LIMIT = 16 * 1024;
    private static final int FIELD_OVERHEAD = 32;
    private static final String BOMB_VALUE = "a".repeat(4000);
    private static final int BOMB_REFERENCES = 20_000;

    private static EventLoopGroup upstreamGroup;
    private static Channel http1Upstream;
    private static Channel http2Upstream;
    private static Channel http2UpstreamWithSmallRequestLimit;
    private static ConnectProxy connectProxy;
    private static final AtomicInteger HTTP1_CONNECTIONS = new AtomicInteger();
    private static final AtomicInteger HTTP2_CONNECTIONS = new AtomicInteger();
    private static final List<String> HTTP2_EVENTS = new CopyOnWriteArrayList<>();

    private static MockServer limited;
    private static MockServerClient limitedClient;
    private static MockServer defaults;
    private static MockServerClient defaultsClient;
    private static MockServer unlimited;
    private static MockServerClient unlimitedClient;
    private static MockServer throughProxy;
    private static MockServerClient throughProxyClient;

    @BeforeClass
    public static void startServers() throws Exception {
        upstreamGroup = new NioEventLoopGroup(2);
        http1Upstream = new ServerBootstrap()
            .group(upstreamGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    HTTP1_CONNECTIONS.incrementAndGet();
                    ch.pipeline().addLast(new HttpRequestDecoder());
                    ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                    ch.pipeline().addLast(new RawHttp1Upstream());
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        http2Upstream = http2Upstream(certificate, Http2Settings.defaultSettings());
        http2UpstreamWithSmallRequestLimit = http2Upstream(certificate, Http2Settings.defaultSettings().maxHeaderListSize(UPSTREAM_REQUEST_LIMIT));
        connectProxy = new ConnectProxy();

        // the test JVM defaults to ERROR, which would drop the WARN entries this class asserts on
        limited = new MockServer(forwarding().maxHeaderSize(LIMIT).proxyPassMappings(Collections.singletonList(ProxyPassMapping.proxyPass("/pass", "http://127.0.0.1:" + port(http1Upstream)))), 0);
        limitedClient = new MockServerClient("127.0.0.1", limited.getLocalPort());
        defaults = new MockServer(forwarding(), 0);
        defaultsClient = new MockServerClient("127.0.0.1", defaults.getLocalPort());
        unlimited = new MockServer(forwarding().maxHeaderSize(Integer.MAX_VALUE), 0);
        unlimitedClient = new MockServerClient("127.0.0.1", unlimited.getLocalPort());
        throughProxy = new MockServer(forwarding().maxHeaderSize(LIMIT).forwardHttpsProxy(new InetSocketAddress("127.0.0.1", connectProxy.port())), 0);
        throughProxyClient = new MockServerClient("127.0.0.1", throughProxy.getLocalPort());
    }

    private static Configuration forwarding() {
        return configuration().logLevel("WARN").forwardProxyHttp2Upgrade(true);
    }

    private static void forgetLogAndForward(MockServerClient client) {
        client.reset();
        client.when(request().withPath("/http1/.*")).forward(forward().withHost("127.0.0.1").withPort(port(http1Upstream)));
        client.when(request().withPath("/http2/.*")).forward(forward().withHost("127.0.0.1").withPort(port(http2Upstream)).withScheme(HttpForward.Scheme.HTTPS));
        client.when(request().withPath("/small/.*")).forward(forward().withHost("127.0.0.1").withPort(port(http2UpstreamWithSmallRequestLimit)).withScheme(HttpForward.Scheme.HTTPS));
    }

    @AfterClass
    public static void stopServers() throws Exception {
        for (MockServerClient client : Arrays.asList(limitedClient, defaultsClient, unlimitedClient, throughProxyClient)) {
            stopQuietly(client);
        }
        for (MockServer mockServer : Arrays.asList(limited, defaults, unlimited, throughProxy)) {
            stopQuietly(mockServer);
        }
        for (Channel upstream : Arrays.asList(http1Upstream, http2Upstream, http2UpstreamWithSmallRequestLimit)) {
            if (upstream != null) {
                upstream.close();
            }
        }
        if (connectProxy != null) {
            connectProxy.close();
        }
        if (upstreamGroup != null) {
            upstreamGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Before
    public void forgetEarlierTests() {
        HTTP2_EVENTS.clear();
        connectProxy.responseHeaderBytes = 0;
        connectProxy.splitAtLastCr = false;
        for (MockServerClient client : Arrays.asList(limitedClient, defaultsClient, unlimitedClient, throughProxyClient)) {
            forgetLogAndForward(client);
        }
    }

    @Test
    public void shouldRelayResponseHeadersOverNettysDefaultLimitOverHttp1() throws Exception {
        Response response = get(defaults, "/http1/headers/" + 20 * 1024);

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.header("set-cookie"), is("session=" + "a".repeat(20 * 1024 - 37)));
        assertThat(response.body, is("ok"));
    }

    @Test
    public void shouldRelayResponseHeadersOfExactlyMaxHeaderSizeOverHttp1AndFailLargerOnes() throws Exception {
        Response atLimit = get(limited, "/http1/headers/" + LIMIT);

        assertThat(atLimit.toString(), atLimit.status, is(200));
        assertThat(atLimit.header("set-cookie").length(), is("session=".length() + LIMIT - 37));
        assertThat(atLimit.body, is("ok"));

        Response overLimit = get(limited, "/http1/headers/" + (LIMIT + 1));

        assertFailedWithOneWarning(limitedClient, overLimit, "upstream response headers are larger than maxHeaderSize (" + LIMIT + " bytes)");
        assertThat("nothing of the response is relayed", overLimit.header("set-cookie"), is(""));
        assertThat("the next forward is served at once, on a new connection", get(limited, "/http1/headers/1000").status, is(200));
    }

    @Test
    public void shouldFailTheSameWayWhenProxyingByHostHeaderAndByProxyPassMapping() throws Exception {
        String reason = "upstream response headers are larger than maxHeaderSize (" + LIMIT + " bytes)";
        String upstream = "127.0.0.1:" + port(http1Upstream);
        assertThat(exchange(limited, "GET", "/unmatched/headers/" + LIMIT, upstream, "").status, is(200));
        forgetLogAndForward(limitedClient);

        assertFailedWithOneWarning(limitedClient, exchange(limited, "GET", "/unmatched/headers/" + (LIMIT + 1), upstream, ""), reason);

        assertThat(get(limited, "/pass/headers/" + LIMIT).status, is(200));
        forgetLogAndForward(limitedClient);

        assertFailedWithOneWarning(limitedClient, get(limited, "/pass/headers/" + (LIMIT + 1)), reason);
    }

    @Test
    public void shouldLimitHttp1ResponseTrailersByMaxHeaderSize() throws Exception {
        // Netty counts HTTP/1.1 trailers on top of the response's headers, here "transfer-encoding: chunked"
        int headers = "transfer-encoding: chunked".length();
        Response within = get(limited, "/http1/trailers/" + (LIMIT - headers));

        assertThat(within.toString(), within.status, is(200));
        assertThat(within.body, is("ok"));

        Response overLimit = get(limited, "/http1/trailers/" + (LIMIT - headers + 1));

        assertFailedWithOneWarning(limitedClient, overLimit, "upstream response headers and trailers are together larger than maxHeaderSize (" + LIMIT + " bytes)");
        assertThat("the next forward is served at once, on a new connection", get(limited, "/http1/headers/1000").status, is(200));
    }

    @Test
    public void shouldLimitHttp1ResponseHeadersAndTrailersExactlyWhenTheirLastLineEndArrivesSplit() throws Exception {
        Response headersAtLimit = get(limited, "/http1/split-headers/" + LIMIT);

        assertThat(headersAtLimit.toString(), headersAtLimit.status, is(200));
        assertThat(headersAtLimit.header("set-cookie").length(), is("session=".length() + LIMIT - 37));
        assertThat(headersAtLimit.body, is("ok"));
        assertFailedWithOneWarning(limitedClient, get(limited, "/http1/split-headers/" + (LIMIT + 1)), "upstream response headers are larger than maxHeaderSize (" + LIMIT + " bytes)");

        forgetLogAndForward(limitedClient);
        int headers = "transfer-encoding: chunked".length();
        Response trailersAtLimit = get(limited, "/http1/split-trailers/" + (LIMIT - headers));

        assertThat(trailersAtLimit.toString(), trailersAtLimit.status, is(200));
        assertThat(trailersAtLimit.body, is("ok"));
        assertFailedWithOneWarning(limitedClient, get(limited, "/http1/split-trailers/" + (LIMIT - headers + 1)), "upstream response headers and trailers are together larger than maxHeaderSize (" + LIMIT + " bytes)");
    }

    @Test
    public void shouldReuseAPooledHttp1ConnectionAfterALargeResponseHead() throws Exception {
        assertThat(get(defaults, "/http1/headers/1000").status, is(200));
        int before = HTTP1_CONNECTIONS.get();

        for (int i = 0; i < 3; i++) {
            Response response = get(defaults, "/http1/headers/" + 100 * 1024);
            assertThat(response.toString(), response.status, is(200));
            assertThat(response.body, is("ok"));
        }

        assertThat(HTTP1_CONNECTIONS.get() - before, is(0));
    }

    @Test
    public void shouldRelayResponseHeadersOverNettysDefaultLimitOverHttp2() throws Exception {
        Response response = get(defaults, "/http2/headers/" + 20 * 1024);

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.header("set-cookie"), is("session=" + "a".repeat(20 * 1024 - 92)));
        assertThat(response.body, is("ok"));
    }

    @Test
    public void shouldRelayAHeaderListOfExactlyMaxHeaderSizeOverHttp2AndFailLargerOnes() throws Exception {
        Response atLimit = get(limited, "/http2/headers/" + LIMIT);

        assertThat(atLimit.toString(), atLimit.status, is(200));
        assertThat(atLimit.header("set-cookie").length(), is("session=".length() + LIMIT - 92));
        assertThat(atLimit.body, is("ok"));

        HTTP2_EVENTS.clear();
        Response overLimit = get(limited, "/http2/headers/" + (LIMIT + 1));

        assertFailedWithOneWarning(limitedClient, overLimit, "upstream response headers are larger than maxHeaderSize (" + LIMIT + " bytes)");
        // a stream error to Netty; MockServer then closes the connection, which carried only this forward
        awaitHttp2Event("goaway:" + Http2Error.NO_ERROR.code());
        assertThat(HTTP2_EVENTS.toString(), not(containsString("goaway:" + Http2Error.PROTOCOL_ERROR.code())));
        assertThat("sent once", requests("/http2/headers/" + (LIMIT + 1)), is(1L));
        assertThat("the next forward is served", get(limited, "/http2/headers/1000").status, is(200));
    }

    @Test
    public void shouldFailAResponseWhoseHeaderBlockIsMoreThanAQuarterOverOverHttp2() throws Exception {
        // a pooled connection, on which a failure that looks like a stale connection is retried
        assertThat(get(limited, "/http2/headers/1000").status, is(200));

        // 'a' takes 5 bits in HPACK's Huffman code, so this block is about 1.9 times the limit
        Response farOver = get(limited, "/http2/headers/" + 3 * LIMIT);

        assertFailedWithOneWarning(limitedClient, farOver, "an upstream response header block is more than a quarter larger than maxHeaderSize (" + LIMIT + " bytes)");
        awaitHttp2Event("goaway:" + Http2Error.PROTOCOL_ERROR.code());
        assertThat("sent once", requests("/http2/headers/" + 3 * LIMIT), is(1L));
        assertThat("the next forward is served", get(limited, "/http2/headers/1000").status, is(200));
    }

    /**
     * About 23 KB on the wire and 80 MB decoded, over 300 times the default limit. What reading it allocates is
     * measured in {@code ForwardHeaderLimitTest}, away from this JVM's buffer leak tracking.
     */
    @Test
    public void shouldFailAResponseWhoseHeaderBlockDecodesFarOverTheLimit() throws Exception {
        Response bomb = get(defaults, "/http2/bomb");

        assertFailedWithOneWarning(defaultsClient, bomb, "upstream response headers are larger than maxHeaderSize (" + DEFAULT_LIMIT + " bytes)");
        assertThat("the next forward is served", get(defaults, "/http2/headers/1000").status, is(200));
    }

    @Test
    public void shouldLimitHttp2ResponseTrailersByMaxHeaderSize() throws Exception {
        Response within = get(limited, "/http2/trailers/" + LIMIT);

        assertThat(within.toString(), within.status, is(200));
        assertThat(within.body, is("ok"));

        Response overLimit = get(limited, "/http2/trailers/" + (LIMIT + 1));

        assertFailedWithOneWarning(limitedClient, overLimit, "upstream response trailers are larger than maxHeaderSize (" + LIMIT + " bytes)");
        assertThat("the next forward is served", get(limited, "/http2/headers/1000").status, is(200));
    }

    @Test
    public void shouldReuseAPooledHttp2ConnectionAfterALargeResponseHead() throws Exception {
        assertThat(get(defaults, "/http2/headers/1000").status, is(200));
        int before = HTTP2_CONNECTIONS.get();

        for (int i = 0; i < 3; i++) {
            Response response = get(defaults, "/http2/headers/" + 100 * 1024);
            assertThat(response.toString(), response.status, is(200));
            assertThat(response.body, is("ok"));
        }

        assertThat(HTTP2_CONNECTIONS.get() - before, is(0));
    }

    @Test
    public void shouldReadHeadersOfAnySizeWhenMaxHeaderSizeIsTheLargestInteger() throws Exception {
        Response http1 = get(unlimited, "/http1/headers/" + 300 * 1024);
        Response http2 = get(unlimited, "/http2/headers/" + 300 * 1024);

        assertThat(http1.toString(), http1.status, is(200));
        assertThat(http1.header("set-cookie").length(), is("session=".length() + 300 * 1024 - 37));
        assertThat(http2.toString(), http2.status, is(200));
        assertThat(http2.header("set-cookie").length(), is("session=".length() + 300 * 1024 - 92));
    }

    @Test
    public void shouldRelayAResponseThroughAnUpstreamProxyWhoseConnectResponseHasLargeHeaders() throws Exception {
        connectProxy.responseHeaderBytes = LIMIT;
        int before = connectProxy.tunnels.get();

        Response response = get(throughProxy, "/http2/headers/" + 20 * 1024);

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.header("set-cookie").length(), is("session=".length() + 20 * 1024 - 92));
        assertThat(response.body, is("ok"));
        assertThat(connectProxy.tunnels.get() - before, is(1));
    }

    @Test
    public void shouldLimitTheUpstreamProxysConnectResponseHeadersExactlyWhenTheirLastLineEndArrivesSplit() throws Exception {
        connectProxy.splitAtLastCr = true;
        connectProxy.responseHeaderBytes = LIMIT;

        Response response = get(throughProxy, "/http2/headers/1000");

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.body, is("ok"));

        connectProxy.responseHeaderBytes = LIMIT + 1;

        assertFailedWithOneWarning(throughProxyClient, get(throughProxy, "/http2/headers/1000"), "the upstream proxy's CONNECT response headers are larger than maxHeaderSize (" + LIMIT + " bytes)");
    }

    @Test
    public void shouldFailAForwardWhenTheUpstreamProxysConnectResponseHeadersAreOverTheLimit() throws Exception {
        connectProxy.responseHeaderBytes = LIMIT + 1;
        long started = System.nanoTime();

        Response response = get(throughProxy, "/http2/headers/1000");

        assertFailedWithOneWarning(throughProxyClient, response, "the upstream proxy's CONNECT response headers are larger than maxHeaderSize (" + LIMIT + " bytes)");
        assertThat("without waiting out the proxy connect timeout", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started), lessThan(5L));
    }

    /**
     * RFC 9113 section 6.5.2 makes {@code SETTINGS_MAX_HEADER_LIST_SIZE} advisory, so the forward client does not
     * apply an upstream's to the requests it sends: the upstream answers for itself (Netty answers 431), whether
     * the request went on a new connection or on a pooled one that had already read the upstream's settings.
     */
    @Test
    public void shouldSendARequestOverTheLimitAnHttp2UpstreamAnnouncedAndRelayItsAnswer() throws Exception {
        String large = "x-large: " + "a".repeat(20 * 1024) + "\r\n";
        for (String method : new String[]{"GET", "POST"}) {
            MockServer mockServer = new MockServer(forwarding(), 0);
            MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
            try {
                forgetLogAndForward(client);
                int before = HTTP2_CONNECTIONS.get();

                Response onANewConnection = exchange(mockServer, method, "/small/headers/1000", large);

                assertThat(method + " " + onANewConnection, onANewConnection.status, is(431));
                assertThat(method + " went on a new connection", HTTP2_CONNECTIONS.get() - before, is(1));

                assertThat(get(mockServer, "/small/headers/1000").status, is(200));
                before = HTTP2_CONNECTIONS.get();

                Response onAPooledConnection = exchange(mockServer, method, "/small/headers/1000", large);

                assertThat(method + " " + onAPooledConnection, onAPooledConnection.status, is(431));
                assertThat(method + " went on the pooled connection", HTTP2_CONNECTIONS.get() - before, is(0));
                assertThat(onAPooledConnection.body, is(onANewConnection.body));
                assertThat("the upstream is still served", get(mockServer, "/small/headers/1000").status, is(200));
            } finally {
                stopQuietly(client);
                stopQuietly(mockServer);
            }
        }
    }

    /**
     * An upstream that reads a header block more than a quarter over the limit it announced treats it as a
     * connection error, as Netty does, and closes the connection without an answer: the client is answered 502.
     * On a pooled connection that is what a stale connection looks like, so an idempotent request is sent once
     * more on a new connection, where it meets the same end; any other request is sent once.
     */
    @Test
    public void shouldAnswer502WhenAnHttp2UpstreamClosesTheConnectionForARequestFarOverItsLimit() throws Exception {
        // 'a' takes 5 bits in HPACK's Huffman code, so this block is about 1.5 times the upstream's limit
        String farOver = "x-large: " + "a".repeat(40 * 1024) + "\r\n";
        for (String method : new String[]{"GET", "POST"}) {
            MockServer mockServer = new MockServer(forwarding(), 0);
            MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
            try {
                forgetLogAndForward(client);
                HTTP2_EVENTS.clear();
                int before = HTTP2_CONNECTIONS.get();

                Response onANewConnection = exchange(mockServer, method, "/small/headers/1000", farOver);

                assertThat(method + " " + onANewConnection, onANewConnection.status, is(502));
                assertThat(onANewConnection.body, is(""));
                assertThat(method + " sent once", blocksRefusedByTheUpstream(), is(1L));
                assertThat(HTTP2_CONNECTIONS.get() - before, is(1));

                assertThat(get(mockServer, "/small/headers/1000").status, is(200));
                HTTP2_EVENTS.clear();
                before = HTTP2_CONNECTIONS.get();

                Response onAPooledConnection = exchange(mockServer, method, "/small/headers/1000", farOver);

                boolean idempotent = method.equals("GET");
                assertThat(method + " " + onAPooledConnection, onAPooledConnection.status, is(502));
                // a POST on a pooled connection the upstream closed is not sent again, and the 502 says so
                assertThat(onAPooledConnection.body, idempotent ? is("") : startsWith("upstream closed the pooled keep-alive connection the request was sent on without answering, and a POST is not sent again"));
                assertThat(method + " sent again only if idempotent", blocksRefusedByTheUpstream(), is(idempotent ? 2L : 1L));
                assertThat(method + " on a new connection only for the second attempt", HTTP2_CONNECTIONS.get() - before, is(idempotent ? 1 : 0));
                assertThat("the upstream is still served", get(mockServer, "/small/headers/1000").status, is(200));
            } finally {
                stopQuietly(client);
                stopQuietly(mockServer);
            }
        }
    }

    private static long blocksRefusedByTheUpstream() {
        return HTTP2_EVENTS.stream().filter(event -> event.startsWith("connection-error:Header size exceeded max allowed size")).count();
    }

    private static void assertFailedWithOneWarning(MockServerClient client, Response response, String reason) {
        assertThat(response.toString(), response.status, is(502));
        assertThat(response.body, is(reason));
        List<String> entries = Arrays.stream(client.retrieveLogMessagesArray(null))
            .filter(entry -> entry.contains("failing forward") || entry.toLowerCase(Locale.ROOT).contains("exception"))
            .collect(Collectors.toList());
        assertThat(entries.toString(), entries.size(), is(1));
        assertThat(entries.get(0), containsString("failing forward to"));
        assertThat(entries.get(0), containsString(reason));
    }

    private static long requests(String path) {
        return HTTP2_EVENTS.stream().filter(event -> event.startsWith("request:" + path)).count();
    }

    private static Response get(MockServer mockServer, String path) throws Exception {
        return exchange(mockServer, "GET", path, "");
    }

    private static Response exchange(MockServer mockServer, String method, String path, String extraHeaders) throws Exception {
        return exchange(mockServer, method, path, "127.0.0.1:" + mockServer.getLocalPort(), extraHeaders);
    }

    private static Response exchange(MockServer mockServer, String method, String path, String host, String extraHeaders) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            socket.setSoTimeout(30_000);
            OutputStream output = socket.getOutputStream();
            output.write((method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n" + extraHeaders + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                received.write(buffer, 0, read);
            }
            return new Response(received.toString(StandardCharsets.ISO_8859_1.name()));
        }
    }

    private static void awaitHttp2Event(String event) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!HTTP2_EVENTS.toString().contains(event)) {
            assertThat("upstream saw " + HTTP2_EVENTS + " and not " + event, System.nanoTime(), lessThan(deadline));
            Thread.sleep(20);
        }
    }

    private static int port(Channel listener) {
        return ((InetSocketAddress) listener.localAddress()).getPort();
    }

    private static int sizeIn(String path) {
        return Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
    }

    private static final class Response {
        private final int status;
        private final Map<String, String> headers = new TreeMap<>();
        private final String body;

        private Response(String raw) {
            int endOfHead = raw.indexOf("\r\n\r\n");
            String[] head = (endOfHead < 0 ? raw : raw.substring(0, endOfHead)).split("\r\n");
            status = head[0].length() >= 12 ? Integer.parseInt(head[0].substring(9, 12)) : -1;
            for (int i = 1; i < head.length; i++) {
                int colon = head[i].indexOf(':');
                headers.put(head[i].substring(0, colon).toLowerCase(Locale.ROOT), head[i].substring(colon + 1).trim());
            }
            body = endOfHead < 0 ? "" : raw.substring(endOfHead + 4);
        }

        private String header(String name) {
            return headers.getOrDefault(name, "");
        }

        @Override
        public String toString() {
            StringBuilder summary = new StringBuilder("status " + status);
            headers.forEach((name, value) -> summary.append(", ").append(name).append(": ").append(value.length() > 60 ? value.length() + " bytes" : value));
            return summary + ", body " + (body.length() > 300 ? body.length() + " bytes" : body);
        }
    }

    /**
     * Writes each response as bytes, so that its header section is the size the path asks for as Netty's HTTP/1.1
     * decoder counts it: the header lines without their line ends.
     */
    private static final class RawHttp1Upstream extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            int size = sizeIn(request.uri());
            String response;
            if (request.uri().startsWith("/http1/trailers/") || request.uri().startsWith("/http1/split-trailers/")) {
                // "x-trailer: " is 11 bytes
                response = "HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n2\r\nok\r\n0\r\nx-trailer: " + "a".repeat(size - 11) + "\r\n\r\n";
            } else {
                // "content-length: 2" is 17 bytes and "set-cookie: session=" 20
                response = "HTTP/1.1 200 OK\r\ncontent-length: 2\r\nset-cookie: session=" + "a".repeat(size - 37) + "\r\n\r\nok";
            }
            if (request.uri().startsWith("/http1/split-")) {
                // the last header or trailer line's LF, and what follows it, arrive after its CR
                int afterLastCr = response.lastIndexOf("\r\n\r\n") + 1;
                ctx.writeAndFlush(Unpooled.copiedBuffer(response.substring(0, afterLastCr), StandardCharsets.ISO_8859_1));
                ctx.executor().schedule(() -> ctx.writeAndFlush(Unpooled.copiedBuffer(response.substring(afterLastCr), StandardCharsets.ISO_8859_1)), 300, TimeUnit.MILLISECONDS);
            } else {
                ctx.writeAndFlush(Unpooled.copiedBuffer(response, StandardCharsets.ISO_8859_1));
            }
        }
    }

    private static Channel http2Upstream(SelfSignedCertificate certificate, Http2Settings settings) throws Exception {
        SslContext sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2))
            .build();
        return new ServerBootstrap()
            .group(upstreamGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                    ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("") {
                        @Override
                        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                            HTTP2_CONNECTIONS.incrementAndGet();
                            ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().initialSettings(settings).build());
                            ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel stream) {
                                    stream.pipeline().addLast(new Http2UpstreamStream());
                                }
                            }));
                            ctx.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext connection, Object msg) {
                                    if (msg instanceof Http2GoAwayFrame) {
                                        HTTP2_EVENTS.add("goaway:" + ((Http2GoAwayFrame) msg).errorCode());
                                    }
                                    ReferenceCountUtil.release(msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext connection, Throwable cause) {
                                    HTTP2_EVENTS.add("connection-error:" + cause.getMessage());
                                }
                            });
                        }
                    });
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
    }

    /**
     * Answers with a header list, or trailers, of the size the path asks for as RFC 9113 section 6.5.2 counts it,
     * and records the resets it is sent and the requests it reads.
     */
    private static final class Http2UpstreamStream extends ChannelInboundHandlerAdapter {
        private String path = "";

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                boolean ended = false;
                if (msg instanceof Http2HeadersFrame) {
                    Http2HeadersFrame headers = (Http2HeadersFrame) msg;
                    path = headers.headers().path().toString();
                    HTTP2_EVENTS.add("request:" + path + (headers.headers().contains("x-large") ? ":x-large:" + headers.headers().get("x-large").length() : ""));
                    ended = headers.isEndStream();
                } else if (msg instanceof Http2DataFrame) {
                    ended = ((Http2DataFrame) msg).isEndStream();
                }
                if (ended) {
                    respond(ctx);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof Http2ResetFrame) {
                HTTP2_EVENTS.add("reset:" + ((Http2ResetFrame) evt).errorCode());
            }
            ctx.fireUserEventTriggered(evt);
        }

        private void respond(ChannelHandlerContext ctx) {
            Http2Headers headers = new DefaultHttp2Headers().status("200");
            if (path.endsWith("/bomb")) {
                // one field, sent once and then referred to from HPACK's dynamic table at a byte a time
                for (int i = 0; i < BOMB_REFERENCES; i++) {
                    headers.add("x-filler", BOMB_VALUE);
                }
                ctx.write(new DefaultHttp2HeadersFrame(headers, false));
                ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("ok", StandardCharsets.UTF_8), true));
            } else if (path.contains("/trailers/")) {
                ctx.write(new DefaultHttp2HeadersFrame(headers, false));
                ctx.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("ok", StandardCharsets.UTF_8), false));
                // "x-trailer" is 9 bytes
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().add("x-trailer", "a".repeat(sizeIn(path) - 9 - FIELD_OVERHEAD)), true));
            } else {
                // ":status" and "200" are 10 bytes, "set-cookie" 10 and "session=" 8
                headers.add("set-cookie", "session=" + "a".repeat(sizeIn(path) - 10 - FIELD_OVERHEAD - 18 - FIELD_OVERHEAD));
                ctx.write(new DefaultHttp2HeadersFrame(headers, false));
                ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("ok", StandardCharsets.UTF_8), true));
            }
        }
    }

    /**
     * An HTTP proxy that answers {@code CONNECT} with {@code 200} and a header of the size asked for, then relays
     * bytes between the two connections.
     */
    private static final class ConnectProxy {
        private final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final AtomicInteger tunnels = new AtomicInteger();
        private volatile int responseHeaderBytes;
        private volatile boolean splitAtLastCr;

        private ConnectProxy() throws IOException {
            daemon(() -> {
                while (!listener.isClosed()) {
                    Socket client = listener.accept();
                    sockets.add(client);
                    daemon(() -> tunnel(client));
                }
            });
        }

        private int port() {
            return listener.getLocalPort();
        }

        private void tunnel(Socket client) throws IOException {
            client.setSoTimeout(30_000);
            StringBuilder head = new StringBuilder();
            InputStream fromClient = client.getInputStream();
            while (head.indexOf("\r\n\r\n") < 0) {
                int read = fromClient.read();
                if (read == -1) {
                    return;
                }
                head.append((char) read);
            }
            String[] target = head.substring("CONNECT ".length(), head.indexOf(" HTTP/1.1")).split(":");
            Socket upstream = new Socket("127.0.0.1", Integer.parseInt(target[1]));
            sockets.add(upstream);
            tunnels.incrementAndGet();
            // "x-proxy: " is 9 bytes
            String filler = responseHeaderBytes > 9 ? "x-proxy: " + "a".repeat(responseHeaderBytes - 9) + "\r\n" : "";
            byte[] response = ("HTTP/1.1 200 Connection established\r\n" + filler + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
            // with splitAtLastCr the last header line's LF, and the blank line, arrive after its CR
            int firstWrite = splitAtLastCr ? response.length - 3 : response.length;
            client.getOutputStream().write(response, 0, firstWrite);
            client.getOutputStream().flush();
            if (firstWrite < response.length) {
                pause();
                client.getOutputStream().write(response, firstWrite, response.length - firstWrite);
                client.getOutputStream().flush();
            }
            daemon(() -> copy(upstream, client));
            copy(client, upstream);
        }

        private static void pause() {
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static void copy(Socket from, Socket to) throws IOException {
            try {
                byte[] buffer = new byte[16 * 1024];
                InputStream input = from.getInputStream();
                OutputStream output = to.getOutputStream();
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } finally {
                from.close();
                to.close();
            }
        }

        private static void daemon(IoTask task) {
            Thread thread = new Thread(() -> {
                try {
                    task.run();
                } catch (IOException closed) {
                    // the listener or a tunnel was closed
                }
            }, "connect-proxy");
            thread.setDaemon(true);
            thread.start();
        }

        private void close() throws IOException {
            listener.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }

    private interface IoTask {
        void run() throws IOException;
    }
}
