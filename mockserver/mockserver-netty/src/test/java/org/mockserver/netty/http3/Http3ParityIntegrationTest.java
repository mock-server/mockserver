package org.mockserver.netty.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TestCertificates;
import org.mockserver.testing.socket.Ipv4DatagramChannelFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.socket.tls.KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory;

/**
 * Integration tests for HTTP/3 parity features: W3C trace-context propagation
 * and mTLS client-certificate capture over QUIC.
 * <p>
 * These tests require the native QUIC transport (BoringSSL) and skip gracefully
 * on platforms where it is not available.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3ParityIntegrationTest {

    private MockServer mockServer;
    private NioEventLoopGroup clientGroup;
    private int responseTimeoutSeconds = 20;

    @Before
    public void setUp() {
        assumeQuicAvailable();
    }

    @After
    public void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
            mockServer = null;
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
            clientGroup = null;
        }
    }

    // ---- W3C trace-context tests ----

    @Test
    public void shouldPropagateTraceparentOverHttp3WhenEnabled() throws Exception {
        // given -- otelPropagateTraceContext enabled
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .otelPropagateTraceContext(true);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/trace-test")
        ).respond(
            response().withStatusCode(200).withBody("traced")
        );

        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String tracestate = "rojo=00f067aa0ba902b7";

        // when -- send HTTP/3 request with traceparent and tracestate
        Http3ResponseCapture result = sendHttp3RequestWithHeaders(
            http3Port, "GET", "/trace-test", null,
            Map.of("traceparent", traceparent, "tracestate", tracestate)
        );

        // then -- response should contain the propagated trace headers
        assertThat("status should be 200", result.status, is("200"));
        assertThat("response body should be 'traced'", result.body, is("traced"));
        assertThat("traceparent should be propagated to response",
            result.headers.get("traceparent"), is(traceparent));
        assertThat("tracestate should be propagated to response",
            result.headers.get("tracestate"), is(tracestate));
    }

    @Test
    public void shouldNotPropagateTraceparentWhenDisabled() throws Exception {
        // given -- default config (otelPropagateTraceContext disabled)
        Configuration config = configuration().http3MaxIdleTimeout(30000L);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/no-trace")
        ).respond(
            response().withStatusCode(200).withBody("no-trace")
        );

        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        // when
        Http3ResponseCapture result = sendHttp3RequestWithHeaders(
            http3Port, "GET", "/no-trace", null,
            Map.of("traceparent", traceparent)
        );

        // then -- response should NOT contain trace headers (propagation disabled)
        assertThat("status should be 200", result.status, is("200"));
        assertThat("traceparent should NOT be on response when propagation disabled",
            result.headers.containsKey("traceparent"), is(false));
    }

    @Test
    public void shouldPropagateTraceparentWithoutTracestateWhenAbsent() throws Exception {
        // given
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .otelPropagateTraceContext(true);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/trace-no-state")
        ).respond(
            response().withStatusCode(200).withBody("ok")
        );

        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        // when -- send only traceparent, no tracestate
        Http3ResponseCapture result = sendHttp3RequestWithHeaders(
            http3Port, "GET", "/trace-no-state", null,
            Map.of("traceparent", traceparent)
        );

        // then -- traceparent propagated, tracestate absent
        assertThat("traceparent should be on response", result.headers.get("traceparent"), is(traceparent));
        assertThat("tracestate should NOT be on response when not in request",
            result.headers.containsKey("tracestate"), is(false));
    }

    // ---- mTLS client-certificate tests ----

    @Test
    public void shouldCaptureClientCertificateOverHttp3() throws Exception {
        // given -- start MockServer with mTLS enabled over H3
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .attemptToProxyIfNoMatchingExpectation(false);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        // Build the client cert material from MockServer's own KeyAndCertificateFactory
        // (this way, the server's trust manager will accept the cert since it trusts
        // its own CA)
        KeyAndCertificateFactory factory = createKeyAndCertificateFactory(config, null);
        if (factory.certificateNotYetCreated()) {
            factory.buildAndSavePrivateKeyAndX509Certificate();
        }
        PrivateKey clientKey = factory.privateKey();
        List<X509Certificate> clientCertChain = factory.certificateChain();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/mtls-test")
        ).respond(
            response().withStatusCode(200).withBody("mtls-ok")
        );

        // when -- send HTTP/3 request with client certificate
        Http3ResponseCapture result = sendHttp3RequestWithClientCert(
            http3Port, "GET", "/mtls-test", null,
            clientKey, clientCertChain.toArray(new X509Certificate[0])
        );

        // then -- the request should succeed and the cert should be captured
        assertThat("status should be 200", result.status, is("200"));
        assertThat("body should be 'mtls-ok'", result.body, is("mtls-ok"));

        // Verify the captured cert is present in the recorded request.
        // Use JAVA format (not JSON) because the JSON pretty-print DTO
        // (HttpRequestPrettyPrintedDTO) omits clientCertificateChain.
        String recordedJava = client.retrieveRecordedRequests(
            request().withPath("/mtls-test"),
            org.mockserver.model.Format.JAVA
        );
        assertThat("should have recorded request", recordedJava, is(notNullValue()));
        assertThat("recorded request should contain clientCertificateChain",
            recordedJava, containsString("clientCertificateChain"));
    }

    @Test
    public void shouldRefuseClientCertificateSignedWithTheBundledCaKeyOverHttp3() throws Exception {
        // given -- mTLS required over H3 with a client CA chain, while MockServer's own CA is the bundled CA
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .attemptToProxyIfNoMatchingExpectation(false)
            .tlsMutualAuthenticationRequired(true)
            .tlsMutualAuthenticationAllowBundledCa(false)
            .tlsMutualAuthenticationCertificateChain("org/mockserver/netty/integration/tls/ca.pem")
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();
        new MockServerClient("127.0.0.1", mockServer.getLocalPort()).when(
            request().withMethod("GET").withPath("/mtls-bundled")
        ).respond(
            response().withStatusCode(200).withBody("mtls-ok")
        );
        TestCertificates.Issued trusted = TestCertificates.clientCertificate("trusted", TestCertificates.issuer(
            PEMToFile.x509FromPEMFile("org/mockserver/netty/integration/tls/ca.pem"),
            PEMToFile.privateKeyFromPEMFile("org/mockserver/netty/integration/tls/ca-key-pkcs8.pem")
        ));
        TestCertificates.Issued attacker = TestCertificates.clientCertificate("attacker", TestCertificates.issuer(
            PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE),
            PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY)
        ));

        // then -- a certificate signed by the chain's CA is accepted and one signed with the bundled CA's key is not
        assertThat(sendHttp3RequestWithClientCert(http3Port, "GET", "/mtls-bundled", null,
            trusted.keyPair.getPrivate(), new X509Certificate[]{trusted.certificate}).status, is("200"));
        String attackerStatus;
        responseTimeoutSeconds = 5;
        try {
            attackerStatus = sendHttp3RequestWithClientCert(http3Port, "GET", "/mtls-bundled", null,
                attacker.keyPair.getPrivate(), new X509Certificate[]{attacker.certificate}).status;
        } catch (Exception handshakeRefused) {
            attackerStatus = "null";
        }
        // "null": no response, because the server ended the QUIC connection
        assertThat(attackerStatus, is("null"));
    }

    @Test
    public void shouldHandleNoCertGracefullyOverHttp3() throws Exception {
        // given -- start MockServer with H3 but no mTLS requirement
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .attemptToProxyIfNoMatchingExpectation(false);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/no-cert")
        ).respond(
            response().withStatusCode(200).withBody("no-cert-ok")
        );

        // when -- send request WITHOUT client certificate
        Http3ResponseCapture result = sendHttp3RequestWithHeaders(
            http3Port, "GET", "/no-cert", null, Map.of()
        );

        // then -- should succeed without error, no cert on the recorded request
        assertThat("status should be 200", result.status, is("200"));
        assertThat("body should be 'no-cert-ok'", result.body, is("no-cert-ok"));

        org.mockserver.model.HttpRequest[] recorded = client.retrieveRecordedRequests(
            request().withPath("/no-cert")
        );
        assertThat("should have recorded request", recorded.length, greaterThanOrEqualTo(1));
        // No client cert should be on the request (null or empty)
        assertThat("recorded request should have no client certificate chain",
            recorded[0].getClientCertificateChain(), anyOf(nullValue(), empty()));
    }

    // ---- default-off tests (existing behaviour unchanged) ----

    @Test
    public void shouldNotAffectBehaviourWithDefaultOtelConfig() throws Exception {
        // given -- default config: otelPropagateTraceContext=false, otelGenerateTraceId=false
        Configuration config = configuration().http3MaxIdleTimeout(30000L);

        mockServer = startWithHttp3(config);
        int http3Port = mockServer.getHttp3Port();

        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/default-test")
        ).respond(
            response().withStatusCode(200).withBody("default-ok")
        );

        // when
        Http3ResponseCapture result = sendHttp3RequestWithHeaders(
            http3Port, "GET", "/default-test", null, Map.of()
        );

        // then -- basic expectation matching works, no trace headers on response
        assertThat("status should be 200", result.status, is("200"));
        assertThat("body should be 'default-ok'", result.body, is("default-ok"));
        assertThat("no traceparent should be on response",
            result.headers.containsKey("traceparent"), is(false));
    }

    // ---- helper methods ----

    /**
     * Response capture holding status, body, and response headers.
     */
    static class Http3ResponseCapture {
        final String status;
        final String body;
        final Map<String, String> headers;

        Http3ResponseCapture(String status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    /**
     * Send an HTTP/3 request with custom headers and capture the full response
     * including response headers.
     */
    private Http3ResponseCapture sendHttp3RequestWithHeaders(
        int port, String method, String path, byte[] body,
        Map<String, String> extraHeaders
    ) throws Exception {
        return doSendHttp3Request(port, method, path, body, extraHeaders, null, null);
    }

    /**
     * Send an HTTP/3 request with a client certificate and capture the response.
     */
    private Http3ResponseCapture sendHttp3RequestWithClientCert(
        int port, String method, String path, byte[] body,
        PrivateKey clientKey, X509Certificate[] clientCertChain
    ) throws Exception {
        return doSendHttp3Request(port, method, path, body, Map.of(), clientKey, clientCertChain);
    }

    private Http3ResponseCapture doSendHttp3Request(
        int port, String method, String path, byte[] body,
        Map<String, String> extraHeaders,
        PrivateKey clientKey, X509Certificate[] clientCertChain
    ) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContextBuilder sslBuilder = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols());

        // if client cert is provided, configure mTLS on the client side
        if (clientKey != null && clientCertChain != null) {
            sslBuilder.keyManager(clientKey, null, clientCertChain);
        }

        QuicSslContext clientSslContext = sslBuilder.build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
                .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamsBidirectional(100)
                .build())
            .bind(0)
            .sync()
            .channel();

        QuicChannel quicChannel = QuicChannel.newBootstrap(clientChannel)
            .handler(new Http3ClientConnectionHandler())
            .remoteAddress(new InetSocketAddress("127.0.0.1", port))
            .connect()
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> bodyQueue = new LinkedBlockingQueue<>();
        Map<String, String> responseHeaders = new ConcurrentHashMap<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                private final StringBuilder bodyBuilder = new StringBuilder();

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                    // capture all response headers
                    headersFrame.headers().forEach(entry -> {
                        String name = entry.getKey().toString();
                        if (!name.startsWith(":")) {
                            responseHeaders.put(name, entry.getValue().toString());
                        }
                    });
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    bodyBuilder.append(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    bodyQueue.offer(bodyBuilder.toString());
                    ctx.close();
                }
            }
        ).sync().getNow();

        // send request headers
        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method(method);
        requestHeaders.headers().path(path);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().authority("127.0.0.1:" + port);

        // add extra headers
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                requestHeaders.headers().add(entry.getKey(), entry.getValue());
            }
        }

        if (body != null && body.length > 0) {
            requestHeaders.headers().add("content-type", "application/json");
            requestStream.writeAndFlush(requestHeaders).sync();
            requestStream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(body)))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                .sync();
        } else {
            requestStream.writeAndFlush(requestHeaders)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                .sync();
        }

        // wait for response
        String status = statusQueue.poll(responseTimeoutSeconds, TimeUnit.SECONDS);
        String responseBody = bodyQueue.poll(responseTimeoutSeconds, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new Http3ResponseCapture(
            status != null ? status : "null",
            responseBody != null ? responseBody : "",
            responseHeaders
        );
    }

    private static void assumeQuicAvailable() {
        try {
            boolean available = io.netty.handler.codec.quic.Quic.isAvailable();
            Assume.assumeTrue(
                "native QUIC transport not available on this platform -- skipping HTTP/3 test",
                available
            );
        } catch (Throwable t) {
            Assume.assumeNoException(
                "native QUIC transport failed to load -- skipping HTTP/3 test",
                t
            );
        }
    }
}
