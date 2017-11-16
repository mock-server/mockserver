package org.mockserver.client.netty;

import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.exact;

public class NettyHttpClientErrorHandlingTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private int freePort;

    @Before
    public void createFixture() {
        freePort = PortFactory.findFreePort();
    }


    @Test
    public void shouldThrowSocketCommunicationExceptionForConnectException() {
        // then
        exception.expect(SocketConnectionException.class);
        exception.expectMessage(containsString("Unable to connect to socket /127.0.0.1:" + freePort));

        // when
        new NettyHttpClient().sendRequest(request().withHeader(HOST.toString(), "127.0.0.1:" + freePort));
    }

    @Test
    public void shouldHandleConnectionClosure() {
        // given
        EchoServer echoServer = new EchoServer(freePort, true, EchoServer.Error.CLOSE_CONNECTION);

        try {
            // then
            exception.expect(RuntimeException.class);
            exception.expectMessage(containsString("Channel set as inactive before valid response has been received"));

            // when
            new NettyHttpClient().sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + freePort));
        } finally {
            echoServer.stop();
        }
    }

    @Test
    public void shouldHandleLargerContentLengthHeader() {
        // given
        EchoServer echoServer = new EchoServer(freePort, true, EchoServer.Error.LARGER_CONTENT_LENGTH);
        long originalMaxSocketTimeout = ConfigurationProperties.maxSocketTimeout();

        try {
            ConfigurationProperties.maxSocketTimeout(5);

            // then
            exception.expect(SocketCommunicationException.class);
            exception.expectMessage(containsString("Response was not received after 5 milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)"));

            // when
            new NettyHttpClient().sendRequest(request().withHeader(HOST.toString(), "127.0.0.1:" + freePort).withBody(exact("this is an example body")).withSecure(true));
        } finally {
            echoServer.stop();
            ConfigurationProperties.maxSocketTimeout(originalMaxSocketTimeout);
        }
    }

    @Test
    public void shouldHandleSmallerContentLengthHeader() {
        // given
        EchoServer echoServer = new EchoServer(freePort, true, EchoServer.Error.SMALLER_CONTENT_LENGTH);

        try {
            // when
            InetSocketAddress socket = new InetSocketAddress("127.0.0.1", freePort);
            HttpResponse httpResponse = new NettyHttpClient().sendRequest(request().withBody(exact("this is an example body")).withSecure(true), socket);

            // then
            assertThat(httpResponse, is(
                    response()
                            .withStatusCode(200)
                            .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length() / 2))
                            .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                            .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                            .withBody(exact("this is an "))
            ));
        } finally {
            echoServer.stop();
        }
    }

}