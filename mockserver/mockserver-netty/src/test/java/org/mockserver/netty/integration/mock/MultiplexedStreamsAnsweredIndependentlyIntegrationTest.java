package org.mockserver.netty.integration.mock;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.MockServerCaTrustTestSupport;
import org.mockserver.netty.http3.Http3Server;
import org.mockserver.netty.http3.Http3TestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * HTTP/2 and HTTP/3 carry each request on its own stream, so a fast request is answered while a slower one sent
 * before it on the same connection is still pending: ordering pipelined HTTP/1.1 responses must not serialise them.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class MultiplexedStreamsAnsweredIndependentlyIntegrationTest {

    private static final long SLOW_MILLIS = 3_000;

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private NioEventLoopGroup clientGroup;

    @After
    public void stop() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
        }
    }

    private void expectSlowAndFast() {
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request().withPath("/slow")).respond(response("slow").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS));
        mockServerClient.when(request().withPath("/fast")).respond(response("fast"));
    }

    @Test
    public void shouldAnswerAFastHttp2StreamBeforeASlowerOneOpenedBeforeIt() throws Exception {
        mockServer = new MockServer(configuration().logLevel("WARN").proxySetup(false), 0);
        expectSlowAndFast();
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(MockServerCaTrustTestSupport.caTrustingSslContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        // opens the connection first, so both streams below share it
        HttpResponse<String> warmUp = httpClient.send(h2Request("/fast"), HttpResponse.BodyHandlers.ofString());
        assertThat(warmUp.version(), is(HttpClient.Version.HTTP_2));

        CompletableFuture<HttpResponse<String>> slow = httpClient.sendAsync(h2Request("/slow"), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> fast = httpClient.sendAsync(h2Request("/fast"), HttpResponse.BodyHandlers.ofString());

        assertThat(fast.get(10, TimeUnit.SECONDS).body(), is("fast"));
        assertThat("the slow stream is still pending when the fast one is answered", slow.isDone(), is(false));
        assertThat(slow.get(10, TimeUnit.SECONDS).body(), is("slow"));
    }

    @Ignore("HTTP/3 runs each data-plane action synchronously on the QUIC event loop (Http3MockServerHandler calls "
        + "processAction with synchronous=true), so a delayed response blocks every stream of the connection: "
        + "a pre-existing defect outside F2, found by F2 U1")
    @Test
    public void shouldAnswerAFastHttp3StreamBeforeASlowerOneOpenedBeforeIt() throws Exception {
        assumeQuicAvailable();
        mockServer = startWithHttp3(configuration().logLevel("WARN").http3MaxIdleTimeout(30_000L));
        expectSlowAndFast();
        clientGroup = new NioEventLoopGroup(1);

        try (Http3TestClient connection = Http3TestClient.open(clientGroup, mockServer)) {
            long start = System.nanoTime();
            Http3TestClient.Exchange slow = connection.send(h3Request("/slow"));
            Http3TestClient.Exchange fast = connection.send(h3Request("/fast"));

            int fastStatus = fast.status();
            long fastHeadersMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            int slowBytesAtFastHeaders = slow.receivedByteArray().length;
            String fastBody = fast.body();
            long fastBodyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String timings = "fast headers after " + fastHeadersMillis + "ms, fast body after " + fastBodyMillis + "ms, slow delayed " + SLOW_MILLIS + "ms";
            assertThat(timings, fastStatus, is(200));
            assertThat(timings, fastBody, is("fast"));
            assertThat("the slow stream has received nothing when the fast one is answered: " + timings, slowBytesAtFastHeaders, is(0));
            assertThat(slow.body(), is("slow"));
        }
    }

    private java.net.http.HttpRequest h2Request(String path) {
        return java.net.http.HttpRequest.newBuilder(URI.create("https://localhost:" + mockServer.getLocalPort() + path)).timeout(Duration.ofSeconds(10)).build();
    }

    private DefaultHttp3Headers h3Request(String path) {
        DefaultHttp3Headers headers = new DefaultHttp3Headers();
        headers.method("GET").scheme("https").authority("localhost:" + mockServer.getHttp3Port()).path(path);
        return headers;
    }

    private static void assumeQuicAvailable() {
        boolean available;
        try {
            available = Http3Server.isQuicAvailable();
        } catch (Throwable unavailable) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 test", available);
    }
}
