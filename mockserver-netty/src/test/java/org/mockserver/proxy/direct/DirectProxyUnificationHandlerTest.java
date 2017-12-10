package org.mockserver.proxy.direct;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.ssl.SslHandler;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyUnificationHandler;
import org.mockserver.proxy.relay.RelayConnectHandler;
import org.mockserver.proxy.socks.SocksProxyHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.google.common.base.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.proxy.Proxy.HTTP_PROXY;
import static org.mockserver.proxy.Proxy.STATE_HANDLER;

public class DirectProxyUnificationHandlerTest {

    @Test
    public void shouldSwitchToSsl() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

        // and - no SSL handler
        assertThat(embeddedChannel.pipeline().get(SslHandler.class), is(nullValue()));

        // when - first part of a 5-byte handshake message
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
            22, // handshake
            3,  // major version
            1,
            0,
            5   // package length (5-byte)
        }));

        // then - should add SSL handlers first
        if (LoggerFactory.getLogger(PortUnificationHandler.class).isTraceEnabled()) {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "SslHandler#0",
                "LoggingHandler#0",
                "HttpProxyUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        } else {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "SslHandler#0",
                "HttpProxyUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        }
    }

    @Test
    public void shouldSwitchToSOCKS() throws IOException, InterruptedException {
        // given - embedded channel
        short localPort = 1234;
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());
        embeddedChannel.attr(HttpProxy.HTTP_CONNECT_SOCKET).set(new InetSocketAddress(localPort));

        // and - mock logger
        RelayConnectHandler.logger = mock(Logger.class);

        // and - no SOCKS handlers
        assertThat(embeddedChannel.pipeline().get(SocksProxyHandler.class), is(nullValue()));
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
        if (LoggerFactory.getLogger(PortUnificationHandler.class).isTraceEnabled()) {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "LoggingHandler#0",
                "SocksCmdRequestDecoder#0",
                "SocksMessageEncoder#0",
                "SocksProxyHandler#0",
                "HttpProxyUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        } else {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "SocksCmdRequestDecoder#0",
                "SocksMessageEncoder#0",
                "SocksProxyHandler#0",
                "HttpProxyUnificationHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        }
    }

    @Test
    public void shouldSwitchToHttp() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        embeddedChannel.attr(STATE_HANDLER).set(new HttpStateHandler());
        embeddedChannel.attr(HTTP_PROXY).set(mock(Proxy.class));
        embeddedChannel.pipeline().addLast(new HttpProxyUnificationHandler());

        // and - no HTTP handlers
        assertThat(embeddedChannel.pipeline().get(HttpServerCodec.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpContentDecompressor.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpObjectAggregator.class), is(nullValue()));

        // when - basic HTTP request
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer("GET /somePath HTTP/1.1\r\nHost: some.random.host\r\n\r\n".getBytes(UTF_8)));

        // then - should add HTTP handlers last
        if (LoggerFactory.getLogger(PortUnificationHandler.class).isTraceEnabled()) {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "LoggingHandler#0",
                "HttpServerCodec#0",
                "HttpContentDecompressor#0",
                "HttpObjectAggregator#0",
                "MockServerServerCodec#0",
                "HttpProxyHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        } else {
            assertThat(embeddedChannel.pipeline().names(), contains(
                "HttpServerCodec#0",
                "HttpContentDecompressor#0",
                "HttpObjectAggregator#0",
                "MockServerServerCodec#0",
                "HttpProxyHandler#0",
                "DefaultChannelPipeline$TailContext#0"
            ));
        }
    }

    @Test
    public void shouldSupportUnknownProtocol() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

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
