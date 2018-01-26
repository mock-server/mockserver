package org.mockserver.client.netty;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.exact;

public class NettyHttpClientErrorHandlingTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldThrowSocketCommunicationExceptionForConnectException() throws Exception {
        // then
        int freePort = PortFactory.findFreePort();
        exception.expect(ExecutionException.class);
        exception.expectMessage(anyOf(
            containsString("Connection refused: /127.0.0.1:" + freePort),
            containsString("Channel closed before valid response")
        ));

        // when
        new NettyHttpClient().sendRequest(request().withHeader(HOST.toString(), "127.0.0.1:" + freePort))
            .get(10, TimeUnit.SECONDS);
    }

    @Test
    public void shouldHandleConnectionClosure() throws Exception {
        // given
        EchoServer echoServer = new EchoServer(true, EchoServer.Error.CLOSE_CONNECTION);

        try {
            // then
            exception.expect(ExecutionException.class);
            exception.expectMessage(containsString("Exception caught before valid response has been received"));

            // when
            new NettyHttpClient().sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + echoServer.getPort()))
                .get(10, TimeUnit.SECONDS);
        } finally {
            echoServer.stop();
        }
    }

    @Test
    public void shouldHandleSmallerContentLengthHeader() throws Exception {
        // given
        EchoServer echoServer = new EchoServer(true, EchoServer.Error.SMALLER_CONTENT_LENGTH);

        try {
            // when
            InetSocketAddress socket = new InetSocketAddress("127.0.0.1", echoServer.getPort());
            HttpResponse httpResponse = new NettyHttpClient().sendRequest(request().withBody(exact("this is an example body")).withSecure(true), socket)
                .get(10, TimeUnit.SECONDS);

            // then
            assertThat(httpResponse, is(
                response()
                    .withStatusCode(200)
                    .withReasonPhrase("OK")
                    .withHeader(header(CONTENT_LENGTH.toString(), "this is an example body".length() / 2))
                    .withHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()))
                    .withHeader(header(ACCEPT_ENCODING.toString(), GZIP.toString() + "," + DEFLATE.toString()))
                    .withBody(exact("this is an "))
            ));
        } finally {
            echoServer.stop();
        }
    }

}
