package org.mockserver.proxy.direct;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.relay.DownstreamProxyRelayHandler;
import org.mockserver.proxy.relay.ProxyRelayHandler;
import org.mockserver.proxy.relay.UpstreamProxyRelayHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Random;

public class DirectProxyUpstreamHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public DirectProxyUpstreamHandler() {
        super(false);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
        final Channel inboundChannel = ctx.channel();
        Bootstrap bootstrap = new Bootstrap()
                .group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(final SocketChannel socketChannel) throws Exception {
                        // downstream
                        ChannelPipeline downstreamPipeline = socketChannel.pipeline();
                        if (PortUnificationHandler.isSslEnabled(ctx)) {
                            downstreamPipeline.addLast(new SslHandler(SSLFactory.createClientSSLEngine()));
                        }

                        if (logger.isDebugEnabled()) {
                            downstreamPipeline.addLast(new LoggingHandler("                -->"));
                        }

                        downstreamPipeline.addLast(new HttpClientCodec());

                        downstreamPipeline.addLast(new HttpContentDecompressor());

                        downstreamPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                        downstreamPipeline.addLast(new DownstreamProxyRelayHandler(ctx.channel(), logger));


                        // upstream
                        ChannelPipeline upstreamPipeline = ctx.channel().pipeline();

                        if (logger.isDebugEnabled()) {
                            upstreamPipeline.addLast(new LoggingHandler("<-- "));
                        }

                        upstreamPipeline.addLast(new UpstreamProxyRelayHandler(socketChannel, logger));
                    }
                });

        final InetSocketAddress remoteSocket = ctx.channel().attr(HttpProxy.REMOTE_SOCKET).get();
        bootstrap.connect(remoteSocket).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(request);
                } else {
                    logger.error("Exception while connecting to ", remoteSocket);
                    ProxyRelayHandler.closeOnFlush(ctx.channel());
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ProxyRelayHandler.closeOnFlush(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception while reading from channel", cause);
        ProxyRelayHandler.closeOnFlush(ctx.channel());
    }
}
