package org.mockserver.proxy.http.direct;

import com.google.common.base.Charsets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.http.relay.BasicHttpDecoder;
import org.mockserver.proxy.http.relay.ProxyRelayHandler;
import org.mockserver.proxy.interceptor.Interceptor;
import org.mockserver.proxy.interceptor.RequestInterceptor;
import org.mockserver.proxy.interceptor.ResponseInterceptor;
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
    private volatile Integer contentLength;
    private volatile int contentSoFar;
    private volatile boolean flushContent;

    public DirectProxyUpstreamHandler(InetSocketAddress remoteSocketAddress, boolean secure, int bufferedCapacity, Interceptor interceptor, String loggerName) {
        this.remoteSocketAddress = remoteSocketAddress;
        this.secure = secure;
        this.bufferedCapacity = bufferedCapacity;
        this.interceptor = interceptor;
        this.logger = LoggerFactory.getLogger(loggerName);
        bufferedMode = bufferedCapacity > 0;
        flushedBuffer = false;
        contentLength = null;
        contentSoFar = 0;
        flushContent = false;
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
                            pipeline.addLast("logger", new LoggingHandler(logger));
                        }

                        // add HTTPS proxy -> server support
                        if (secure) {
                            SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                            engine.setUseClientMode(true);
                            pipeline.addLast("proxy -> server ssl", new SslHandler(engine));
                        }

                        // add handler
                        pipeline.addLast(new ProxyRelayHandler(inboundChannel, bufferedCapacity, new ResponseInterceptor(), logger));
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
                    logger.warn("Failed to connect to: " + remoteSocketAddress, future.cause());
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (outboundChannel.isActive()) {
            if (bufferedMode && channelBuffer.isReadable()) {
                flushedBuffer = true;
                logger.debug("CHANNEL INACTIVE: " + channelBuffer.toString(Charsets.UTF_8));
                outboundChannel.writeAndFlush(interceptor.intercept(ctx, channelBuffer, logger)).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            channelBuffer.clear();
                            // flushed entire buffer upstream so close connection
                            outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                        } else {
                            logger.warn("Failed to send flush channel buffer", future.cause());
                            future.channel().close();
                        }
                    }
                });

            } else {
                outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (bufferedMode && outboundChannel.isActive() && channelBuffer.isReadable()) {
            flushedBuffer = true;
            logger.debug("CHANNEL READ COMPLETE: " + channelBuffer.toString(Charsets.UTF_8));
            outboundChannel.writeAndFlush(interceptor.intercept(ctx, channelBuffer, logger)).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        channelBuffer.clear();
                    } else {
                        logger.warn("Failed to write to: " + remoteSocketAddress, future.cause());
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
            final ByteBuf chunk = (ByteBuf) msg;
            if (flushedBuffer) {
                bufferedMode = false;
            }
            if (bufferedMode) {

                flushContent = false;

                if (contentLength != null) {
                    contentSoFar += chunk.readableBytes();
                } else {
                    // find content length
                    BasicHttpDecoder basicHttpDecoder = new BasicHttpDecoder(Unpooled.copiedBuffer(chunk));
                    contentLength = basicHttpDecoder.getContentLength();
                    contentSoFar = (chunk.readableBytes() - basicHttpDecoder.getContentStart());
                }

//            logger.warn("CHUNK:                     ---\n-\n" + Unpooled.copiedBuffer(chunk).toString(Charsets.UTF_8) + "\n-\n");
//                logger.warn("CONTENT-SO-FAR-PRE-CHUNK:  --- " + (contentSoFar - Unpooled.copiedBuffer(chunk).toString(Charsets.UTF_8).length()));
//                logger.warn("CHUNK-SIZE:                --- " + chunk.readableBytes());
//                logger.warn("CONTENT-SO-FAR-PRE-CHUNK:  --- " + contentSoFar);
//                if (contentLength != null) {
//                    logger.warn("CONTENT-REMAINING:         --- " + (contentLength - contentSoFar));
//                    logger.warn("CONTENT-LENGTH:            --- " + contentLength);
//                }

                if (contentLength != null) {
                    logger.trace("Flushing buffer as all content received");
                    flushContent = (contentSoFar >= contentLength) || (chunk.readableBytes() == 0);
                }
                try {
                    channelBuffer.writeBytes(chunk);
                    ctx.channel().read();
                } catch (IndexOutOfBoundsException iobe) {
                    logger.trace("Flushing buffer upstream and switching to chunked mode as downstream response too large");
                    bufferedMode = false;
                    // write and flush buffer upstream
                    if (outboundChannel.isActive() && channelBuffer.isReadable()) {
                        logger.debug("CHANNEL READ EX: " + chunk.toString(Charsets.UTF_8));
                        outboundChannel.writeAndFlush(channelBuffer).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    // write and flush this chunk upstream in case this single chunk is too large for buffer
                                    channelRead(ctx, chunk);
                                } else {
                                    logger.warn("Failed to write to: " + remoteSocketAddress, future.cause());
                                    future.channel().close();
                                }
                            }
                        });
                    }
                }
            } else {
                bufferedMode = false;
                if (outboundChannel.isActive()) {
                    logger.debug("CHANNEL READ NOT-BUFFERING: " + chunk.toString(Charsets.UTF_8));
                    outboundChannel.writeAndFlush(chunk).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                // was able to flush out data, start to read the next chunk
                                ctx.channel().read();
                            } else {
                                logger.warn("Failed to write to: " + remoteSocketAddress, future.cause());
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
