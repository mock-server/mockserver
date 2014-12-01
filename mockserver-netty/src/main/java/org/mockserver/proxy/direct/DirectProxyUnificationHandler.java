package org.mockserver.proxy.direct;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.proxy.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DirectProxyUnificationHandler extends PortUnificationHandler {

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(new DirectProxyUpstreamHandler());
    }
}
