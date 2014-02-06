package org.mockserver.proxy.http.relay;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.proxy.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyRelayHandler extends ChannelDuplexHandler {

    private final Logger logger;
    private final Interceptor interceptor;
    private final int bufferedCapacity;
    private volatile Channel relayChannel;
    private volatile ByteBuf channelBuffer;
    private volatile boolean bufferedMode;
    private volatile boolean flushedBuffer;
    private volatile Integer contentLength;
    private volatile int contentSoFar;
    private volatile boolean flushContent;

    public ProxyRelayHandler(Channel relayChannel, int bufferedCapacity, Interceptor interceptor, Logger logger) {
        this.relayChannel = relayChannel;
        this.bufferedCapacity = bufferedCapacity;
        this.interceptor = interceptor;
        this.logger = logger;
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
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (relayChannel.isActive()) {
            if (bufferedMode && channelBuffer.isReadable()) {
                flushedBuffer = true;
                logger.debug("CHANNEL INACTIVE: " + channelBuffer.toString(Charsets.UTF_8));
                relayChannel.writeAndFlush(interceptor.intercept(ctx, channelBuffer, logger)).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            channelBuffer.clear();
                            // flushed entire buffer upstream so close connection
                            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                        } else {
                            logger.warn("Failed to send flush channel buffer", future.cause());
                            future.channel().close();
                        }
                    }
                });

            } else {
                relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (bufferedMode && relayChannel.isActive() && channelBuffer.isReadable()) {
            flushedBuffer = true;
            relayChannel.writeAndFlush(interceptor.intercept(ctx, channelBuffer, logger)).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        channelBuffer.clear();
                    } else {
                        logger.warn("Failed to send flush channel buffer", future.cause());
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
                    logger.trace("Flushing buffer and switching to chunked mode as buffer full");
                    flushContent = true;
                }
                if (flushContent) {
                    flushedBuffer = true;
                    if (relayChannel.isActive() && channelBuffer.isReadable()) {
                        logger.debug("CHANNEL READ EX: " + chunk.toString(Charsets.UTF_8));
                        relayChannel.writeAndFlush(channelBuffer).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    channelBuffer.clear();
                                    // write and flush this chunk upstream in case this single chunk is too large for buffer
                                    channelRead(ctx, chunk);
                                } else {
                                    logger.warn("Failed to send flush channel buffer [" + channelBuffer + "]", future.cause());
                                    future.channel().close();
                                }
                            }
                        });
                    }
                }
            } else {
                bufferedMode = false;
                if (relayChannel.isActive()) {
                    logger.debug("CHANNEL READ NOT-BUFFERING: " + chunk.toString(Charsets.UTF_8));
                    relayChannel.writeAndFlush(chunk).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                // was able to flush out data, start to read the next chunk
                                ctx.channel().read();
                            } else {
                                logger.warn("Failed to send flush chunk [" + chunk + "]", future.cause());
                                future.channel().close();
                            }
                        }
                    });
                }
            }
        } else {
            if (relayChannel.isActive()) {
                relayChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            logger.warn("Failed to send flush msg [" + msg + "]", future.cause());
                            future.channel().close();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("Exception caught", cause);
        Channel ch = ctx.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
