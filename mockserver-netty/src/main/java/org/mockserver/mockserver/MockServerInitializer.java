package org.mockserver.mockserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.server.unification.PortUnificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockServerInitializer extends PortUnificationHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MockServerMatcher mockServerMatcher;
    private final MockServer mockServer;

    public MockServerInitializer(MockServerMatcher mockServerMatcher, MockServer mockServer) {
        this.mockServerMatcher = mockServerMatcher;
        this.mockServer = mockServer;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler(logger));
        }

        boolean isSecure = false;
        if (ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).get() != null) {
            isSecure = ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).get();
        }
        pipeline.addLast(new MockServerServerCodec(isSecure));

        // add mock server handlers
        pipeline.addLast(new MockServerHandler(mockServer, mockServerMatcher, ctx.channel().attr(MockServer.LOG_FILTER).get()));
    }
}
