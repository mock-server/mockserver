package org.mockserver.mock.action;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
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

/**
 * @author jamesdbloom
 */
public class ProxyToInvalidSocketIntegrationTest {

    private static ClientAndServer clientAndServer;

    private static EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();

    private static NettyHttpClient httpClient = new NettyHttpClient(clientEventLoopGroup, null);

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
        if (clientAndServer != null) {
            clientAndServer.stop();
        }
    }

    @Before
    public void reset() {
        clientAndServer.reset();
    }

    @Test
    public void shouldNotForwardRequestWithInvalidHostHead() throws Exception {
        // when
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + PortFactory.findFreePort()),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        assertThat(responseSettableFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));
    }

    @Test
    public void shouldVerifyReceivedRequests() throws Exception {
        // given
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + PortFactory.findFreePort()),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        assertThat(responseSettableFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));

        // then
        clientAndServer.verify(request()
            .withPath("/some_path"));
        clientAndServer.verify(request()
            .withPath("/some_path"), VerificationTimes.exactly(1));
    }

}
