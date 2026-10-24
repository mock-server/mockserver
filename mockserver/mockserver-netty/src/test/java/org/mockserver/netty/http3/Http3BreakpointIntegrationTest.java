package org.mockserver.netty.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3Headers;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.netty.MockServer;

import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Request and response breakpoints pause HTTP/3 requests as they pause HTTP/1.1 and HTTP/2 ones: the decision from the
 * breakpoint client reaches the HTTP/3 caller, and a caller that goes away while its request is paused leaves the
 * server serving others. Skipped when the QUIC native library is missing.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3BreakpointIntegrationTest {

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private NioEventLoopGroup clientGroup;

    @Before
    public void startServer() {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable notLoaded) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 breakpoint test", available);
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
        // generous: the pause must be resolved by the client's reply, not by auto-continue
        mockServer = startWithHttp3(configuration().http3MaxIdleTimeout(30000L).breakpointTimeoutMillis(30_000L));
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        clientGroup = new NioEventLoopGroup(2);
    }

    @After
    public void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
    }

    @Test(timeout = 60_000)
    public void shouldModifyAMockResponseAtAResponseBreakpointOverHttp3() throws Exception {
        mockServerClient.when(request("/h3-breakpoint-modify")).respond(response("original-body"));
        CountDownLatch paused = new CountDownLatch(1);
        mockServerClient.addBreakpoint(
            request("/h3-breakpoint-modify"),
            EnumSet.of(BreakpointPhase.RESPONSE),
            null,
            (httpRequest, httpResponse) -> {
                paused.countDown();
                return response().withStatusCode(299).withBody("modified-by-breakpoint-client");
            },
            null
        );

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange exchange = client.send(requestHeaders("/h3-breakpoint-modify"));

            assertThat(exchange.status(), is(299));
            assertThat(exchange.body(), is("modified-by-breakpoint-client"));
        }
        assertThat(paused.await(5, TimeUnit.SECONDS), is(true));
    }

    @Test(timeout = 60_000)
    public void shouldKeepServingWhenTheCallerGoesAwayWhileItsRequestIsPaused() throws Exception {
        mockServerClient.when(request("/h3-breakpoint-paused")).respond(response("should-not-be-returned"));
        mockServerClient.when(request("/h3-after")).respond(response("after"));
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        mockServerClient.addBreakpoint(
            request("/h3-breakpoint-paused"),
            EnumSet.of(BreakpointPhase.REQUEST),
            httpRequest -> {
                paused.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return response().withStatusCode(418).withBody("aborted-after-the-caller-left");
            },
            null,
            null
        );

        Http3TestClient leaving = Http3TestClient.open(clientGroup, mockServer);
        Http3TestClient.Exchange abandoned = leaving.send(requestHeaders("/h3-breakpoint-paused"));
        assertThat(paused.await(10, TimeUnit.SECONDS), is(true));
        leaving.close();
        // the decision now arrives for a stream whose connection has gone: its write is dropped, not leaked
        release.countDown();
        assertThat(abandoned.dataFrames(), is(0));

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange after = client.send(requestHeaders("/h3-after"));
            assertThat(after.status(), is(200));
            assertThat(after.body(), is("after"));
        }
    }

    private Http3Headers requestHeaders(String path) {
        return new DefaultHttp3Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }
}
