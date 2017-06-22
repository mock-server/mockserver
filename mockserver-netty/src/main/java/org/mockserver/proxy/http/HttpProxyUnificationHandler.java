package org.mockserver.proxy.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class HttpProxyUnificationHandler extends PortUnificationHandler {

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(new MockServerServerCodec(isSslEnabledDownstream(ctx.channel())));
        pipeline.addLast(new HttpProxyHandler(
                ctx.channel().attr(Proxy.HTTP_PROXY).get(),
                ctx.channel().attr(Proxy.REQUEST_LOG_FILTER).get(),
                ctx.channel().attr(Proxy.REQUEST_RESPONSE_LOG_FILTER).get()
        ));
    }

}
