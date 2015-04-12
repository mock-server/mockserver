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
    private final boolean secure;
    private final MockServer mockServer;

    public MockServerInitializer(MockServerMatcher mockServerMatcher, MockServer mockServer, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
        this.secure = secure;
        this.mockServer = mockServer;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler(logger));
        }

        pipeline.addLast(new MockServerServerCodec(secure));

        // add mock server handlers
        pipeline.addLast(new MockServerHandler(mockServer, mockServerMatcher, ctx.channel().attr(MockServer.LOG_FILTER).get()));
    }
}
