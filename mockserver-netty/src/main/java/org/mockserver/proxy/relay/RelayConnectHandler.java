package org.mockserver.proxy.relay;

import com.google.common.annotations.VisibleForTesting;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.mock.action.ActionHandler.REMOTE_SOCKET;
import static org.mockserver.proxy.Proxy.HTTP_CONNECT_SOCKET;
import static org.mockserver.proxy.Proxy.PROXYING;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;
import static org.mockserver.unification.PortUnificationHandler.isSslEnabledDownstream;
import static org.mockserver.unification.PortUnificationHandler.isSslEnabledUpstream;

@ChannelHandler.Sharable
public abstract class RelayConnectHandler<T> extends SimpleChannelInboundHandler<T> {
    @VisibleForTesting
    public static Logger logger = LoggerFactory.getLogger(RelayConnectHandler.class);
    private final String host;
    private final int port;

    public RelayConnectHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext serverCtx, final T request) throws Exception {
        Bootstrap bootstrap = new Bootstrap()
            .group(serverCtx.channel().eventLoop())
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
                                serverCtx.channel().attr(PROXYING).set(Boolean.TRUE);

                                // downstream
                                ChannelPipeline downstreamPipeline = clientCtx.channel().pipeline();

                                if (isSslEnabledDownstream(serverCtx.channel())) {
                                    downstreamPipeline.addLast(nettySslContextFactory().createClientSslContext().newHandler(clientCtx.alloc(), host, port));
                                }

                                if (logger.isTraceEnabled()) {
                                    downstreamPipeline.addLast(new LoggingHandler("downstream                -->"));
                                }

                                downstreamPipeline.addLast(new HttpClientCodec());

                                downstreamPipeline.addLast(new HttpContentDecompressor());

                                downstreamPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                                downstreamPipeline.addLast(new DownstreamProxyRelayHandler(serverCtx.channel(), logger));


                                // upstream
                                ChannelPipeline upstreamPipeline = serverCtx.channel().pipeline();

                                if (isSslEnabledUpstream(serverCtx.channel())) {
                                    upstreamPipeline.addLast(nettySslContextFactory().createServerSslContext().newHandler(serverCtx.alloc()));
                                }

                                if (logger.isTraceEnabled()) {
                                    upstreamPipeline.addLast(new LoggingHandler("upstream <-- "));
                                }

                                upstreamPipeline.addLast(new HttpServerCodec(8192, 8192, 8192));

                                upstreamPipeline.addLast(new HttpContentDecompressor());

                                upstreamPipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                                upstreamPipeline.addLast(new UpstreamProxyRelayHandler(serverCtx.channel(), clientCtx.channel(), logger));
                            }
                        });
                }
            });

        final InetSocketAddress remoteSocket = getDownstreamSocket(serverCtx.channel());
        bootstrap.connect(remoteSocket).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    failure("Connection failed to " + remoteSocket, future.cause(), serverCtx, failureResponse(request));
                }
            }
        });
    }

    private InetSocketAddress getDownstreamSocket(Channel channel) {
        if (channel.attr(REMOTE_SOCKET).get() != null) {
            return channel.attr(REMOTE_SOCKET).get();
        } else if (channel.attr(HTTP_CONNECT_SOCKET).get() != null) {
            return channel.attr(HTTP_CONNECT_SOCKET).get();
        } else {
            throw new IllegalStateException("Trying to connect to remote socket but no remote socket has been set");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        failure("Exception caught by CONNECT proxy handler -> closing pipeline ", cause, ctx, failureResponse(null));
    }

    private void failure(String message, Throwable cause, ChannelHandlerContext ctx, Object response) {
        if (shouldNotIgnoreException(cause)) {
            logger.warn(message, cause);
        }
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

    protected void removeHandler(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.toMap().containsValue(channelHandler)) {
            pipeline.remove(channelHandler);
        }
    }

}
