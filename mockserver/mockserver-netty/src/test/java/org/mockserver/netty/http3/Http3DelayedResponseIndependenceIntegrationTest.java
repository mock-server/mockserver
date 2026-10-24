package org.mockserver.netty.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3Headers;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A delayed HTTP/3 response waits on the scheduler, not on the QUIC event loop, so a fast stream is answered while
 * a slow one on the same event loop is still waiting out its delay. Skipped when the QUIC native library is missing.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3DelayedResponseIndependenceIntegrationTest {

    private static final long DELAY_MILLIS = 2000;

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static NioEventLoopGroup clientGroup;

    @BeforeClass
    public static void startServer() {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable notLoaded) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 delay test", available);
        mockServer = startWithHttp3(configuration().http3MaxIdleTimeout(30000L));
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request("/slow")).respond(response().withBody("slow").withDelay(TimeUnit.MILLISECONDS, DELAY_MILLIS));
        mockServerClient.when(request("/fast")).respond(response().withBody("fast"));
        clientGroup = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shouldAnswerFastStreamWhileSlowStreamOnSameConnectionWaitsOutItsDelay() throws Exception {
        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            assertFastAnsweredBeforeSlow(client, client);
        }
    }

    @Test
    public void shouldAnswerFastConnectionWhileSlowConnectionWaitsOutItsDelay() throws Exception {
        try (Http3TestClient slowClient = Http3TestClient.open(clientGroup, mockServer);
             Http3TestClient fastClient = Http3TestClient.open(clientGroup, mockServer)) {
            assertFastAnsweredBeforeSlow(slowClient, fastClient);
        }
    }

    private static void assertFastAnsweredBeforeSlow(Http3TestClient slowClient, Http3TestClient fastClient) throws Exception {
        mockServerClient.clear(request("/slow"), org.mockserver.model.ClearType.LOG);
        long slowSent = System.nanoTime();
        Http3TestClient.Exchange slow = slowClient.send(requestHeaders("/slow"));
        awaitSlowRequestReceived();

        Http3TestClient.Exchange fast = fastClient.send(requestHeaders("/fast"));
        assertThat(fast.status(), is(200));
        assertThat(fast.body(), is("fast"));
        // the slow stream has had nothing back yet: its response waits on the scheduler, not on the event loop
        assertThat(slow.dataFrames(), is(0));

        assertThat(slow.status(), is(200));
        assertThat(slow.body(), is("slow"));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - slowSent), greaterThanOrEqualTo(DELAY_MILLIS));
        // answered once: one header section, one DATA frame and no second HEADERS frame
        assertThat(slow.receivedTrailers(), is(false));
        assertThat(slow.dataFrames(), is(1));
    }

    private static void awaitSlowRequestReceived() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (mockServerClient.retrieveRecordedRequests(request("/slow")).length == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the slow request was not received within 10 seconds");
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
    }

    private static Http3Headers requestHeaders(String path) {
        return new DefaultHttp3Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }
}
