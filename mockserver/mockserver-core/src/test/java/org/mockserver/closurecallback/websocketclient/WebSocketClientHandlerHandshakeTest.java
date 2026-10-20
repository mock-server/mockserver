package org.mockserver.closurecallback.websocketclient;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class WebSocketClientHandlerHandshakeTest {

    private static List<String> handshakeLines(String authorization) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec());
        channel.pipeline().addLast(new WebSocketClientHandler(new MockServerLogger(), "test-client-id", new InetSocketAddress("localhost", 1080), "", mock(WebSocketClient.class), false, authorization));
        channel.pipeline().fireChannelActive();
        StringBuilder written = new StringBuilder();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            written.append(((ByteBuf) outbound).toString(StandardCharsets.US_ASCII));
            ((ByteBuf) outbound).release();
        }
        channel.finishAndReleaseAll();
        return Arrays.asList(written.toString().split("\r\n"));
    }

    @Test
    public void shouldSendNoOriginHeaderBecauseItIsNotABrowser() throws Exception {
        List<String> lines = handshakeLines(null);

        assertThat(lines.get(0), is("GET /_mockserver_callback_websocket HTTP/1.1"));
        assertThat(lines.stream().anyMatch(line -> line.toLowerCase().startsWith("origin:")), is(false));
        assertThat(lines, hasItem("X-CLIENT-REGISTRATION-ID: test-client-id"));
    }

    @Test
    public void shouldSendTheAuthorizationHeaderOnlyWhenOneIsConfigured() throws Exception {
        assertThat(handshakeLines("Bearer some.jwt.value"), hasItem("authorization: Bearer some.jwt.value"));
        assertThat(handshakeLines(null).stream().anyMatch(line -> line.toLowerCase().startsWith("authorization:")), is(false));
        assertThat(handshakeLines(" ").stream().anyMatch(line -> line.toLowerCase().startsWith("authorization:")), is(false));
    }
}
