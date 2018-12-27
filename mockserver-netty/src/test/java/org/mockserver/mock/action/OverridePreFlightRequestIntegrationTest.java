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

import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class OverridePreFlightRequestIntegrationTest {

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
        stopQuietly(clientAndServer);
    }

    @Before
    public void reset() {
        clientAndServer.reset();
    }

    @Test
    public void shouldReturnDefaultPreFlightResponse() throws Exception {
        // when
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withMethod("OPTIONS")
                    .withPath("/expectation")
                    .withHeader("Access-Control-Request-Method", "PUT")
                    .withHeader(HOST.toString(), "localhost:" + clientAndServer.getLocalPort())
                    .withHeader("Origin", "http://127.0.0.1:1234"),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        HttpResponse response = responseSettableFuture.get(10, TimeUnit.SECONDS);
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getHeader("access-control-allow-origin"), containsInAnyOrder("*"));
        assertThat(response.getHeader("access-control-allow-methods"), containsInAnyOrder("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getHeader("access-control-allow-headers"), containsInAnyOrder("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getHeader("access-control-expose-headers"), containsInAnyOrder("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"));
        assertThat(response.getHeader("access-control-max-age"), containsInAnyOrder("300"));
        assertThat(response.getHeader("x-cors"), containsInAnyOrder("MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.enableCORSForAPI=false"));
        assertThat(response.getFirstHeader("version"), not(isEmptyString()));
    }

    @Test
    public void shouldReturnOveriddenPreFlightResponse() throws Exception {
        // given
        clientAndServer
            .when(
                request()
                    .withMethod("OPTIONS")
            )
            .respond(
                response()
                    .withHeader("access-control-allow-origin", "*")
                    .withHeader("access-control-allow-methods", "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE")
                    .withHeader("access-control-allow-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization, Authorization")
                    .withHeader("access-control-expose-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization, Authorization")
                    .withHeader("access-control-max-age", "300")
            );

        // when
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withMethod("OPTIONS")
                    .withPath("/expectation")
                    .withHeader("Access-Control-Request-Method", "PUT")
                    .withHeader(HOST.toString(), "localhost:" + clientAndServer.getLocalPort())
                    .withHeader("Origin", "http://127.0.0.1:1234"),
                new InetSocketAddress(clientAndServer.getLocalPort())
            );

        // then
        HttpResponse response = responseSettableFuture.get(10, TimeUnit.SECONDS);
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getHeader("access-control-allow-origin"), containsInAnyOrder("*"));
        assertThat(response.getHeader("access-control-allow-methods"), containsInAnyOrder("CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"));
        assertThat(response.getHeader("access-control-allow-headers"), containsInAnyOrder("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization, Authorization"));
        assertThat(response.getHeader("access-control-expose-headers"), containsInAnyOrder("Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization, Authorization"));
        assertThat(response.getHeader("access-control-max-age"), containsInAnyOrder("300"));
        assertThat(response.getFirstHeader("x-cors"), isEmptyString());
        assertThat(response.getFirstHeader("version"), isEmptyString());
    }

}
