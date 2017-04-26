package org.mockserver.client.netty;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
import static org.mockserver.model.StringBody.exact;

public class NettyHttpClientTest {

    private static EchoServer echoServer;
    private static int freePort;
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void startEchoServer() {
        freePort = PortFactory.findFreePort();
        echoServer = new EchoServer(freePort, false);
    }

    @AfterClass
    public static void stopEchoServer() {
        echoServer.stop();
    }

    @Test
    public void shouldSendBasicRequest() {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(outboundRequest("0.0.0.0", freePort, "", request()));

        // then
        assertThat(httpResponse, is(
                response()
                        .withStatusCode(200)
                        .withHeader(header(HOST.toString(), "0.0.0.0:" + freePort))
                        .withHeader(header(CONTENT_LENGTH.toString(), 0))
                        .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                        .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
        ));
    }

    @Test
    public void shouldSendComplexRequest() {
        // given
        NettyHttpClient nettyHttpClient = new NettyHttpClient();

        // when
        HttpResponse httpResponse = nettyHttpClient.sendRequest(outboundRequest("0.0.0.0", freePort, "",
                request()
                        .withHeader(header("some_header_name", "some_header_value"))
                        .withHeader(header("another_header_name", "first_header_value", "second_header_value"))
                        .withCookie(cookie("some_cookie_name", "some_cookie_value"))
                        .withCookie(cookie("another_cookie_name", "another_cookie_value"))
                        .withBody(exact("this is an example body"))
        ));

        // then
        assertThat(httpResponse, is(
                response()
                        .withStatusCode(200)
                        .withHeader(header(HOST.toString(), "0.0.0.0:" + freePort))
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