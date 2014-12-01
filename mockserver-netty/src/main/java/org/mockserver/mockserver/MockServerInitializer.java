package org.mockserver.mockserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.client.netty.codec.MockServerClientCodec;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class MockServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MockServerMatcher mockServerMatcher;
    private final boolean secure;
    private final MockServer mockServer;

    public MockServerInitializer(MockServerMatcher mockServerMatcher, MockServer mockServer, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
        this.secure = secure;
        this.mockServer = mockServer;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        // add HTTPS support
        if (secure) {
            pipeline.addLast(new SslHandler(SSLFactory.createServerSSLEngine()));
        }

        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler());
        }

        // add msg <-> HTTP
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpContentDecompressor());
        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerServerCodec(secure));

        // add mock server handlers
        pipeline.addLast(new MockServerHandler(mockServerMatcher, mockServer));
    }
}
