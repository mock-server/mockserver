package org.mockserver.proxy.direct;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DirectProxyUnificationHandler extends PortUnificationHandler {

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(new MockServerServerCodec(isSslEnabledDownstream(ctx.channel())));
        pipeline.addLast(new HttpProxyHandler(
                        ctx.channel().attr(Proxy.HTTP_PROXY).get(),
                        ctx.channel().attr(Proxy.REQUEST_LOG_FILTER).get(),
                        ctx.channel().attr(Proxy.REQUEST_RESPONSE_LOG_FILTER).get(),
                        ctx.channel().attr(HttpProxy.ONWARD_SSL_UNKNOWN).get())
        );
    }
}
