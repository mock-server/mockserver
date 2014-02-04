package org.mockserver.proxy.http.direct;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.http.relay.ProxyRelayHandler;
import org.mockserver.proxy.interceptor.Interceptor;
import org.mockserver.proxy.interceptor.RequestInterceptor;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class DirectProxyUpstreamHandler extends ChannelDuplexHandler {

    private final Logger logger;
    private final InetSocketAddress remoteSocketAddress;
    private final boolean secure;
    private final int bufferedCapacity;
    private final Interceptor interceptor;
    private volatile Channel outboundChannel;
    private volatile ByteBuf channelBuffer;
    private volatile boolean bufferedMode;
    private volatile boolean flushedBuffer;

    public DirectProxyUpstreamHandler(InetSocketAddress remoteSocketAddress, boolean secure, int bufferedCapacity, Interceptor interceptor, String loggerName) {
        this.remoteSocketAddress = remoteSocketAddress;
        this.secure = secure;
        this.bufferedCapacity = bufferedCapacity;
        this.interceptor = interceptor;
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.channelBuffer = Unpooled.directBuffer(bufferedCapacity);
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (channelBuffer.refCnt() >= 1) {
            channelBuffer.release();
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        // Create a default pipeline implementation.
                        ChannelPipeline pipeline = ch.pipeline();

                        // add logging
                        if (logger.isDebugEnabled()) {
                            pipeline.addLast("logger", new LoggingHandler("                -->"));
                        }

                        // add HTTPS proxy -> server support
                        if (secure) {
                            SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                            engine.setUseClientMode(true);
                            pipeline.addLast("proxy -> server ssl", new SslHandler(engine));
                        }

                        // add handler
                        pipeline.addLast(new ProxyRelayHandler(inboundChannel, bufferedCapacity, new RequestInterceptor(), "                -->"));
                    }
                })
                .option(ChannelOption.AUTO_READ, false);
        ChannelFuture channelFuture = bootstrap.connect(remoteSocketAddress);
        outboundChannel = channelFuture.channel();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    channelBuffer.clear();
                    bufferedMode = bufferedCapacity > 0;
                    flushedBuffer = false;
                    // connection complete start to read first data
                    inboundChannel.read();
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (bufferedMode && outboundChannel.isActive()) {
            flushedBuffer = true;
            outboundChannel.writeAndFlush(interceptor.intercept(ctx, channelBuffer, logger)).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        channelBuffer.clear();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            // handle normal request
            final ByteBuf chunk = (ByteBuf) msg;
            if (flushedBuffer) {
                bufferedMode = false;
            }
            if (bufferedMode) {
                try {
                    channelBuffer.writeBytes(chunk);
                    ctx.channel().read();
                } catch (IndexOutOfBoundsException iobe) {
                    logger.trace("Flushing buffer upstream and switching to chunked mode as downstream response too large");
                    bufferedMode = false;
                    // write and flush buffer upstream
                    if (outboundChannel.isActive()) {
                        outboundChannel.writeAndFlush(channelBuffer).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    // write and flush this chunk upstream in case this single chunk is too large for buffer
                                    channelRead(ctx, chunk);
                                } else {
                                    future.channel().close();
                                }
                            }
                        });
                    }
                }
            } else {
                bufferedMode = false;
                if (outboundChannel.isActive()) {
                    outboundChannel.writeAndFlush(chunk).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                // was able to flush out data, start to read the next chunk
                                ctx.channel().read();
                            } else {
                                future.channel().close();
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        Channel ch = ctx.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
