package org.mockserver.proxy.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.ssl.SslHandler;
import org.junit.Test;
import org.mockserver.proxy.socks.SocksProxyHandler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class HttpProxyUnificationHandlerTest {

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

        // then
        assertThat(embeddedChannel.pipeline().get(SslHandler.class), is(not(nullValue())));
    }

    @Test
    public void shouldSwitchToSOCKS() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

        // and - no SOCKS handlers
        assertThat(embeddedChannel.pipeline().get(SocksProxyHandler.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(SocksMessageEncoder.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(SocksInitRequestDecoder.class), is(nullValue()));

        // when - SOCKS INIT message
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                0x05, // SOCKS5
                1,    // 1 authentication method
                0x00  // NO_AUTH
        }));

        // then
        assertThat(embeddedChannel.pipeline().get(SocksProxyHandler.class), is(not(nullValue())));
        assertThat(embeddedChannel.pipeline().get(SocksMessageEncoder.class), is(not(nullValue())));
    }

    @Test
    public void shouldSwitchToHttp() {
        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

        // and - no HTTP handlers
        assertThat(embeddedChannel.pipeline().get(HttpServerCodec.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpContentDecompressor.class), is(nullValue()));
        assertThat(embeddedChannel.pipeline().get(HttpObjectAggregator.class), is(nullValue()));

        // when - basic HTTP request
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer("GET /somePath HTTP/1.1\r\nHost: some.random.host\r\n\r\n".getBytes()));

        // then
        assertThat(embeddedChannel.pipeline().get(HttpServerCodec.class), is(not(nullValue())));
        assertThat(embeddedChannel.pipeline().get(HttpContentDecompressor.class), is(not(nullValue())));
        assertThat(embeddedChannel.pipeline().get(HttpObjectAggregator.class), is(not(nullValue())));
    }

}