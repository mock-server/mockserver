package org.mockserver.netty.integration.proxy;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.PortFactory;
import org.mockserver.verify.VerificationTimes;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ProxyToInvalidSocketIntegrationTest {

    private static ClientAndServer clientAndServer;

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ProxyToInvalidSocketIntegrationTest.class.getSimpleName() + "-eventLoop"));

    private static final NettyHttpClient httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null);

    @BeforeClass
    public static void startServer() {
        clientAndServer = startClientAndServer();
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(clientAndServer);
    }

    @Before
    public void reset() {
        clientAndServer.reset();
    }

    @Test
    public void shouldNotForwardRequestWithInvalidHostHead() throws Exception {
        // when
        Future<HttpResponse> responseFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + PortFactory.findFreePort()),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        assertThat(responseFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));
    }

    @Test
    public void shouldVerifyReceivedRequests() throws Exception {
        // given
        Future<HttpResponse> responseFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + PortFactory.findFreePort()),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        assertThat(responseFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));

        // then
        clientAndServer.verify(request()
            .withPath("/some_path"));
        clientAndServer.verify(request()
            .withPath("/some_path"), VerificationTimes.exactly(1));
    }

}
