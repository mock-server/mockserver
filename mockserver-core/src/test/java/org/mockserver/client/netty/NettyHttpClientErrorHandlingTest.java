package org.mockserver.client.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.PortFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.stop.Stop.stopQuietly;

public class NettyHttpClientErrorHandlingTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(NettyHttpClientErrorHandlingTest.class.getSimpleName() + "-eventLoop"));
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldThrowSocketCommunicationExceptionForConnectException() throws Exception {
        // then
        int freePort = PortFactory.findFreePort();
        exception.expect(ExecutionException.class);
        exception.expectMessage(anyOf(
            containsString("Connection refused: /127.0.0.1:" + freePort),
            containsString("Connection refused: no further information: /127.0.0.1:" + freePort),
            containsString("Channel closed before valid response")
        ));

        // when
        new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false, null).sendRequest(request().withHeader(HOST.toString(), "127.0.0.1:" + freePort))
            .get(10, TimeUnit.SECONDS);
    }

    @Test
    public void shouldHandleConnectionClosure() throws Exception {
        // given
        EchoServer echoServer = new EchoServer(true, false, EchoServer.Error.CLOSE_CONNECTION);

        try {
            // then
            exception.expect(ExecutionException.class);
            exception.expectMessage(anyOf(
                containsString("Connection reset by peer"),
                containsString("Channel set as inactive before valid response has been received"),
                containsString("Channel handler removed before valid response has been received")
            ));

            // when
            new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false, null).sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + echoServer.getPort()))
                .get(10, TimeUnit.SECONDS);
        } finally {
            stopQuietly(echoServer);
        }
    }

    @Test
    public void shouldHandleSmallerContentLengthHeader() throws Exception {
        // given
        EchoServer echoServer = new EchoServer(true, false, EchoServer.Error.SMALLER_CONTENT_LENGTH);

        try {
            // when
            InetSocketAddress socket = new InetSocketAddress("127.0.0.1", echoServer.getPort());
            HttpResponse httpResponse = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false, null)
                .sendRequest(
                    request()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.TEXT_PLAIN.toString())
                        .withBody(exact("this is an example body"))
                        .withSecure(true),
                    socket
                )
                .get(10, TimeUnit.SECONDS);

            // then
            assertThat(httpResponse, is(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
                    .withHeader(CONTENT_TYPE.toString(), "text/plain")
                    .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                    .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                    .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length() / 2))
                    .withBody(exact("this is an ", MediaType.TEXT_PLAIN))
            ));
        } finally {
            stopQuietly(echoServer);
        }
    }

}
