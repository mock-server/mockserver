package org.mockserver.mockserver;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.mockserver.callback.WebSocketServerHandler;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.unification.PortUnificationHandler;

import static org.mockserver.mock.HttpStateHandler.STATE_HANDLER;
import static org.mockserver.mockserver.MockServer.MOCK_SERVER;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class MockServerInitializer extends PortUnificationHandler {

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(new WebSocketServerHandler(ctx.channel().attr(STATE_HANDLER).get().getWebSocketClientRegistry()));
        pipeline.addLast(new MockServerServerCodec(isSslEnabledUpstream(ctx.channel())));

        pipeline.addLast(new MockServerHandler(
            ctx.channel().attr(MOCK_SERVER).get(),
            ctx.channel().attr(STATE_HANDLER).get())
        );
    }

}
