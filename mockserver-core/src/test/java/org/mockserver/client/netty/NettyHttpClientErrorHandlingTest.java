package org.mockserver.client.netty;

import com.google.common.net.MediaType;
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

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
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
        new NettyHttpClient().sendRequest(outboundRequest("127.0.0.1", freePort, "", request()));
    }

    @Test
    @Ignore
    public void shouldHandleConnectionClosure() {
        // given
        EchoServer echoServer = new EchoServer(freePort, true, EchoServer.Error.CLOSE_CONNECTION);

        try {
            // then
            exception.expect(RuntimeException.class);
            exception.expectMessage(containsString("Connection reset by peer"));

            // when
            new NettyHttpClient().sendRequest(outboundRequest("127.0.0.1", freePort, "", request()).setSecure(true));
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
            new NettyHttpClient().sendRequest(outboundRequest("127.0.0.1", freePort, "", request().withBody(exact("this is an example body"))).setSecure(true));
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
            HttpResponse httpResponse = new NettyHttpClient().sendRequest(outboundRequest("127.0.0.1", freePort, "", request().withBody(exact("this is an example body"))).setSecure(true));

            // then
            assertThat(httpResponse, is(
                    response()
                            .withHeader(header(HOST, "localhost:" + freePort))
                            .withHeader(header(CONTENT_LENGTH, "this is an example body".length() / 2))
                            .withHeader(header(ACCEPT_ENCODING, HttpHeaders.Values.GZIP + "," + HttpHeaders.Values.DEFLATE))
                            .withHeader(header(CONNECTION, HttpHeaders.Values.KEEP_ALIVE))
                            .withHeader(header(CONTENT_TYPE, MediaType.create("text", "plain").toString()))
                            .withBody(exact("this is an "))
            ));
        } finally {
            echoServer.stop();
        }
    }

}