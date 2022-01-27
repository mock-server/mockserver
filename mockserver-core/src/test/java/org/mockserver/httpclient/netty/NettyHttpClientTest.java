package org.mockserver.httpclient.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.stop.Stop.stopQuietly;

public class NettyHttpClientTest {

    private static EchoServer echoServer;
    @Rule
    public ExpectedException exception = ExpectedException.none();
    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(NettyHttpClientTest.class.getSimpleName() + "-eventLoop"));
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @BeforeClass
    public static void startEchoServer() {
        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @AfterClass
    public static void stopEchoServer() {
        stopQuietly(echoServer);
    }

    @Test
    public void shouldSendBasicRequest() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request().withHeader("Host", "0.0.0.0:" + echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(HOST.toString(), "0.0.0.0:" + echoServer.getPort()))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
        ));
    }

    @Test
    public void shouldSendBasicRequestToAnotherIpAndPort() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request().withHeader("Host", "www.google.com"), new InetSocketAddress("0.0.0.0", echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(HOST.toString(), "www.google.com"))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
        ));
    }

    @Test
    public void shouldSendBasicRequestToAnotherIpAndPortWithNoHostHeader() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request(), new InetSocketAddress("0.0.0.0", echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
        ));
    }

    @Test
    public void shouldSendComplexRequestWithStringBody() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
            request()
                .withHeader("Host", "0.0.0.0:" + echoServer.getPort())
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString())
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(exact("this is an example body"))
        ).get(10, TimeUnit.HOURS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withContentType(MediaType.PLAIN_TEXT_UTF_8)
                .withHeader(header(HOST.toString(), "0.0.0.0:" + echoServer.getPort()))
                .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length()))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(COOKIE.toString(), "some_cookie_name=some_cookie_value; another_cookie_name=another_cookie_value"))
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(exact("this is an example body", MediaType.PLAIN_TEXT_UTF_8))
        ));
    }

    @Test
    public void shouldSendComplexRequestWithStringBodyAndNotContentType() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
            request()
                .withHeader("Host", "0.0.0.0:" + echoServer.getPort())
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(exact("this is an example body"))
        ).get(10, TimeUnit.HOURS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(HOST.toString(), "0.0.0.0:" + echoServer.getPort()))
                .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length()))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(COOKIE.toString(), "some_cookie_name=some_cookie_value; another_cookie_name=another_cookie_value"))
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(exact("this is an example body"))
        ));
    }

    @Test
    public void shouldSendComplexRequestWithBinaryBody() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient(mockServerLogger, clientEventLoopGroup, null, false);

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
            request()
                .withContentType(MediaType.ANY_VIDEO_TYPE)
                .withHeader("Host", "0.0.0.0:" + echoServer.getPort())
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(binary("this is an example body".getBytes(UTF_8)))
        ).get(10, TimeUnit.HOURS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withContentType(MediaType.ANY_VIDEO_TYPE)
                .withHeader(header(HOST.toString(), "0.0.0.0:" + echoServer.getPort()))
                .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length()))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(COOKIE.toString(), "some_cookie_name=some_cookie_value; another_cookie_name=another_cookie_value"))
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(binary("this is an example body".getBytes(UTF_8), MediaType.ANY_VIDEO_TYPE))
        ));
    }

}
