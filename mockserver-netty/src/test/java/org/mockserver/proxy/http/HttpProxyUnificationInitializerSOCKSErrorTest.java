package org.mockserver.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mockserver.MockServerUnificationInitializer;
import org.mockserver.proxy.socks.Socks5ProxyHandler;
import org.mockserver.scheduler.Scheduler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.slf4j.event.Level.TRACE;

public class HttpProxyUnificationInitializerSOCKSErrorTest {

    @Test
    public void shouldHandleErrorsDuringSOCKSConnection() {
        // given - embedded channel
        short localPort = 1234;
        final LifeCycle lifeCycle = mock(LifeCycle.class);
        when(lifeCycle.getScheduler()).thenReturn(mock(Scheduler.class));
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new MockServerUnificationInitializer(lifeCycle, new HttpStateHandler(new MockServerLogger(), mock(Scheduler.class)), mock(ActionHandler.class)));

        // and - no SOCKS handlers
        assertThat(embeddedChannel.pipeline().get(Socks5ProxyHandler.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(SocksMessageEncoder.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(SocksInitRequestDecoder.class), is(nullValue()));

        // when - SOCKS INIT message
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
            (byte) 0x05,                                        // SOCKS5
            (byte) 0x02,                                        // 1 authentication method
            (byte) 0x00,                                        // NO_AUTH
            (byte) 0x02,                                        // AUTH_PASSWORD
        }));


        // then - INIT response
        assertThat(ByteBufUtil.hexDump((ByteBuf) embeddedChannel.readOutbound()), is(Hex.encodeHexString(new byte[]{
            (byte) 0x05,                                        // SOCKS5
            (byte) 0x00,                                        // NO_AUTH
        })));

        // and then - should add SOCKS handlers first
        if (MockServerLogger.isEnabled(TRACE)) {
            assertThat(String.valueOf(embeddedChannel.pipeline().names()), embeddedChannel.pipeline().names(), contains(
                "LoggingHandler#0",
                "Socks5CommandRequestDecoder#0",
                "Socks5ServerEncoder#0",
                "Socks5ProxyHandler#0",
                "PortUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        } else {
            assertThat(String.valueOf(embeddedChannel.pipeline().names()), embeddedChannel.pipeline().names(), contains(
                "Socks5CommandRequestDecoder#0",
                "Socks5ServerEncoder#0",
                "Socks5ProxyHandler#0",
                "PortUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        }

        // and when - SOCKS CONNECT command
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
            (byte) 0x05,                                        // SOCKS5
            (byte) 0x01,                                        // command type CONNECT
            (byte) 0x00,                                        // reserved (must be 0x00)
            (byte) 0x01,                                        // address type IPv4
            (byte) 0x7f, (byte) 0x00, (byte) 0x00, (byte) 0x01, // ip address
            (byte) (localPort & 0xFF00), (byte) localPort       // port
        }));

        // then - CONNECT response
        assertThat(ByteBufUtil.hexDump((ByteBuf) embeddedChannel.readOutbound()), is(Hex.encodeHexString(new byte[]{
            (byte) 0x05,             // SOCKS5
            (byte) 0x01,             // general failure (caused by connection failure)
            (byte) 0x00,             // reserved (must be 0x00)
            (byte) 0x03,             // address type domain
            (byte) 0x00,             // domain of length 0
            (byte) 0x00, (byte) 0x00 // port 0
        })));

        // then - channel is closed after error
        assertThat(embeddedChannel.isOpen(), is(false));
    }

    @Test
    public void shouldSwitchToHttp() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.pipeline().addLast(new MockServerUnificationInitializer(mock(LifeCycle.class), new HttpStateHandler(new MockServerLogger(), mock(Scheduler.class)), mock(ActionHandler.class)));

        // and - no HTTP handlers
        assertThat(embeddedChannel.pipeline().get(HttpServerCodec.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpContentDecompressor.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpObjectAggregator.class), is(nullValue()));

        // when - basic HTTP request
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer("GET /somePath HTTP/1.1\r\nHost: some.random.host\r\n\r\n".getBytes(UTF_8)));

        // then - should add HTTP handlers last
        if (MockServerLogger.isEnabled(TRACE)) {
            assertThat(String.valueOf(embeddedChannel.pipeline().names()), embeddedChannel.pipeline().names(), contains(
                "LoggingHandler#0",
                "HttpServerCodec#0",
                "HttpContentDecompressor#0",
                "HttpContentLengthRemover#0",
                "HttpObjectAggregator#0",
                "CallbackWebSocketServerHandler#0",
                "DashboardWebSocketServerHandler#0",
                "MockServerServerCodec#0",
                "MockServerHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        } else {
            assertThat(String.valueOf(embeddedChannel.pipeline().names()), embeddedChannel.pipeline().names(), contains(
                "HttpServerCodec#0",
                "HttpContentDecompressor#0",
                "HttpContentLengthRemover#0",
                "HttpObjectAggregator#0",
                "CallbackWebSocketServerHandler#0",
                "DashboardWebSocketServerHandler#0",
                "MockServerServerCodec#0",
                "MockServerHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        }
    }

    @Test
    public void shouldSupportUnknownProtocol() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new MockServerUnificationInitializer(mock(LifeCycle.class), new HttpStateHandler(new MockServerLogger(), mock(Scheduler.class)), mock(ActionHandler.class)));

        // and - channel open
        assertThat(embeddedChannel.isOpen(), is(true));

        // when - basic HTTP request
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer("UNKNOWN_PROTOCOL".getBytes(UTF_8)));

        // then - should add no handlers
        assertThat(embeddedChannel.pipeline().names(), contains(
            "DefaultChannelPipeline$TailContext#0"
        ));

        // and - close channel
        assertThat(embeddedChannel.isOpen(), is(false));
    }

}
