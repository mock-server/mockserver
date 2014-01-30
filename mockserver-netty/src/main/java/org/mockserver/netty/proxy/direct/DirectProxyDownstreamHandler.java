package org.mockserver.netty.proxy.direct;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectProxyDownstreamHandler extends ChannelDuplexHandler {

    protected static final Logger logger = LoggerFactory.getLogger(" ------------------> ");
    private final Channel inboundChannel;
    private final int bufferedCapacity;
    private ByteBuf channelBuffer;
    private boolean bufferedMode;
    private boolean flushedBuffer;
    private Integer contentLength;
    private int contentSoFar;
    private boolean flushContent;

    public DirectProxyDownstreamHandler(Channel inboundChannel, int bufferedCapacity) {
        this.inboundChannel = inboundChannel;
        this.bufferedCapacity = bufferedCapacity;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.channelBuffer = Unpooled.directBuffer(bufferedCapacity);
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if(channelBuffer.refCnt() >= 1) {
            channelBuffer.release();
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("ACTIVE");
        bufferedMode = bufferedCapacity > 0;
        flushedBuffer = false;
        contentLength = null;
        contentSoFar = 0;
        flushContent = false;
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("INACTIVE");
        if (inboundChannel.isActive()) {
            if (bufferedMode) {
                flushedBuffer = true;

                inboundChannel.writeAndFlush(channelBuffer).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            channelBuffer.clear();
                            // flushed entire buffer upstream so close connection
                            inboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                        } else {
                            future.channel().close();
                        }
                    }
                });

            } else {
                inboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.warn("EVENT: " + evt);
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.warn("CHANNEL-READ-COMPLETE: " + ctx);
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        final ByteBuf chunk = (ByteBuf) msg;
        logger.warn("CHANNEL-READ");
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
                if (inboundChannel.isActive()) {
                    inboundChannel.writeAndFlush(channelBuffer).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                channelBuffer.clear();
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
            if (inboundChannel.isActive()) {
                inboundChannel.writeAndFlush(chunk).addListener(new ChannelFutureListener() {
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

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logger.warn("WRITE");
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        logger.warn("FLUSH");
        super.flush(ctx);
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
