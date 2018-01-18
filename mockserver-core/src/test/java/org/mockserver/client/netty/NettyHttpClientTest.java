package org.mockserver.client.netty;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.exact;

public class NettyHttpClientTest {

    private static EchoServer echoServer;
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void startEchoServer() {
        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopEchoServer() {
        echoServer.stop();
    }

    @Test
    public void shouldSendBasicRequest() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request().withHeader("Host", "0.0.0.0:" + echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
                .withHeader(header(HOST.toString(), "0.0.0.0:" + echoServer.getPort()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
        ));
    }

    @Test
    public void shouldSendBasicRequestToAnotherIpAndPort() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request().withHeader("Host", "www.google.com"), new InetSocketAddress("0.0.0.0", echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(HOST.toString(), "www.google.com"))
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
        ));
    }

    @Test
    public void shouldSendBasicRequestToAnotherIpAndPortWithNoHostHeader() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(request(), new InetSocketAddress("0.0.0.0", echoServer.getPort()))
            .get(10, TimeUnit.SECONDS);

        // then
        assertThat(httpResponse, is(
            response()
                .withStatusCode(200)
                .withReasonPhrase("OK")
                .withHeader(header(CONTENT_LENGTH.toString(), 0))
                .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
        ));
    }

    @Test
    public void shouldSendComplexRequest() throws Exception {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
            request()
                .withHeader("Host", "0.0.0.0:" + echoServer.getPort())
                .withHeader(header("some_header_name", "some_header_value"))
                .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                .withBody(exact("this is an example body"))
        ).get(10, TimeUnit.SECONDS);;

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

}
