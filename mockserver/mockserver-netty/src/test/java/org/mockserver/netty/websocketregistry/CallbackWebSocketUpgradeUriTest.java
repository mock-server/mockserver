package org.mockserver.netty.websocketregistry;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * The callback WebSocket is upgraded only for its path as sent: a percent-encoded form is not the control plane
 * to the router or to the log redaction, so it passes on as an ordinary request.
 */
public class CallbackWebSocketUpgradeUriTest {

    @Test
    public void shouldMatchTheRawPathOnly() {
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket"), is(true));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket?capabilities=x"), is(true));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket#fragment"), is(true));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/%5Fmockserver_callback_websocket"), is(false));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket%3Fx"), is(false));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket_evil"), is(false));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mockserver_callback_websocket/extra"), is(false));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri("/_mock"), is(false));
        assertThat(CallbackWebSocketServerHandler.isCallbackUpgradeUri(null), is(false));
    }

    @Test
    public void shouldNotUpgradeAPercentEncodedCallbackPath() {
        MockServerLogger logger = new MockServerLogger(CallbackWebSocketUpgradeUriTest.class);
        Scheduler scheduler = new Scheduler(configuration(), logger, true);
        HttpState httpState = new HttpState(configuration(), logger, scheduler);
        List<Object> passedOn = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new CallbackWebSocketServerHandler(httpState), new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                passedOn.add(msg);
            }
        });
        FullHttpRequest encoded = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/%5Fmockserver_callback_websocket");
        encoded.headers().set("Host", "localhost").set("Upgrade", "websocket").set("Connection", "Upgrade")
            .set("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==").set("Sec-WebSocket-Version", "13");
        try {
            channel.writeInbound(encoded);

            assertThat("passed on as an ordinary request", passedOn, contains(encoded));
            assertThat("no 101 handshake was written", channel.readOutbound(), nullValue());
        } finally {
            encoded.release();
            channel.finishAndReleaseAll();
            httpState.stop();
            scheduler.shutdown();
        }
    }
}
