package org.mockserver.mock.action.http;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakeException;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpWebSocketResponse;
import org.mockserver.scheduler.Scheduler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The Netty request built for a WebSocket handshake is released whether the handshake succeeds or throws.
 */
public class HttpWebSocketResponseActionHandlerRequestReleaseTest {

    @Test
    public void shouldReleaseHandshakeRequestWhenHandshakeThrows() {
        // given - a version 13 upgrade with no Sec-WebSocket-Key, which the handshaker rejects by throwing
        EmbeddedChannel connection = new EmbeddedChannel(new HttpServerCodec(), new ChannelInboundHandlerAdapter());
        RecordingHandler handler = new RecordingHandler();

        // when
        assertThrows(WebSocketServerHandshakeException.class, () -> handler.handle(
            HttpWebSocketResponse.webSocketResponse(),
            connection.pipeline().lastContext(),
            upgrade().removeHeader("Sec-WebSocket-Key")));

        // then
        assertThat(handler.built.refCnt(), is(0));
        connection.finishAndReleaseAll();
    }

    @Test
    public void shouldReleaseHandshakeRequestWhenHandshakeSucceeds() {
        // given
        EmbeddedChannel connection = new EmbeddedChannel(new HttpServerCodec(), new ChannelInboundHandlerAdapter());
        RecordingHandler handler = new RecordingHandler();

        // when
        handler.handle(HttpWebSocketResponse.webSocketResponse(), connection.pipeline().lastContext(), upgrade());

        // then
        assertThat(handler.built.refCnt(), is(0));
        connection.finishAndReleaseAll();
    }

    private static HttpRequest upgrade() {
        return request("/ws")
            .withMethod("GET")
            .withHeader("Host", "localhost")
            .withHeader("Upgrade", "websocket")
            .withHeader("Connection", "Upgrade")
            .withHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
            .withHeader("Sec-WebSocket-Version", "13");
    }

    private static final class RecordingHandler extends HttpWebSocketResponseActionHandler {
        private FullHttpRequest built;

        private RecordingHandler() {
            super(new MockServerLogger(), mock(Scheduler.class), configuration(), null);
        }

        @Override
        FullHttpRequest buildNettyRequest(HttpRequest request) {
            built = super.buildNettyRequest(request);
            return built;
        }
    }
}
