package org.mockserver.proxy.relay;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public abstract class RelayConnectHandler<T> extends SimpleChannelInboundHandler<T> {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void channelRead0(final ChannelHandlerContext serverCtx, final T request) throws Exception {
        final Channel inboundChannel = serverCtx.channel();
        Bootstrap bootstrap = new Bootstrap()
                .group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext clientCtx) throws Exception {
                        serverCtx.channel()
                                .writeAndFlush(successResponse(request))
                                .addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                                        removeCodecSupport(serverCtx);

                                        // downstream
                                        ChannelPipeline downstreamPipeline = clientCtx.channel().pipeline();
                                        if (PortUnificationHandler.isSslEnabledDownstream(serverCtx.channel())) {
                                            downstreamPipeline.addLast(new SslHandler(SSLFactory.createClientSSLEngine()));
                                        }

                                        if (logger.isDebugEnabled()) {
                                            downstreamPipeline.addLast(new LoggingHandler("                -->"));
                                        }

                                        downstreamPipeline.addLast(new HttpClientCodec());

                                        downstreamPipeline.addLast(new HttpContentDecompressor());

                                        downstreamPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                                        downstreamPipeline.addLast(new DownstreamProxyRelayHandler(serverCtx.channel(), logger));


                                        // upstream
                                        ChannelPipeline upstreamPipeline = serverCtx.channel().pipeline();
                                        if (PortUnificationHandler.isSslEnabledUpstream(serverCtx.channel())) {
                                            upstreamPipeline.addLast(new SslHandler(SSLFactory.createServerSSLEngine()));
                                        }

                                        if (logger.isDebugEnabled()) {
                                            upstreamPipeline.addLast(new LoggingHandler("<-- "));
                                        }

                                        upstreamPipeline.addLast(new HttpServerCodec());

                                        upstreamPipeline.addLast(new HttpContentDecompressor());

                                        upstreamPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                                        upstreamPipeline.addLast(new UpstreamProxyRelayHandler(clientCtx.channel(), logger));
                                    }
                                });
                    }
                });

        final InetSocketAddress remoteSocket = serverCtx.channel().attr(HttpProxy.REMOTE_SOCKET).get();
        bootstrap.connect(remoteSocket).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    failure("Connection failed to 127.0.0.1:" + remoteSocket, future.cause(), serverCtx, failureResponse(request));
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        failure("Exception caught by http proxy CONNECT handler closing pipeline", cause, ctx, failureResponse(null));
    }

    private void failure(String message, Throwable cause, ChannelHandlerContext ctx, Object response) {
        logger.warn(message, cause);
        Channel channel = ctx.channel();
        channel.writeAndFlush(response);
        if (channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    protected abstract void removeCodecSupport(ChannelHandlerContext ctx);

    protected abstract Object successResponse(Object request);

    protected abstract Object failureResponse(Object request);

    protected void removeHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> handlerType) {
        if (pipeline.get(handlerType) != null) {
            pipeline.remove(handlerType);
        }
    }
}
