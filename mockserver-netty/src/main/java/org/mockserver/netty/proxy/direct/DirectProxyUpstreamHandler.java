package org.mockserver.netty.proxy.direct;

import com.google.common.base.Charsets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DirectProxyUpstreamHandler extends ChannelDuplexHandler {

    protected static final Logger logger = LoggerFactory.getLogger(" <- ");
    private final String remoteHost;
    private final int remotePort;
    private final boolean secure;
    private volatile Channel outboundChannel;
    private ByteBuf channelBuffer;
    private int bufferedCapacity;
    private boolean bufferedMode;
    private boolean flushedBuffer;

    public DirectProxyUpstreamHandler(String remoteHost, int remotePort, boolean secure, int bufferedCapacity) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.secure = secure;
        this.bufferedCapacity = bufferedCapacity;
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
        logger.warn("ACTIVE");
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new DirectProxyDownstreamInitializer(inboundChannel, secure, bufferedCapacity))
                .option(ChannelOption.AUTO_READ, false);
        ChannelFuture channelFuture = bootstrap.connect(remoteHost, remotePort);
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
        logger.warn("INACTIVE");
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.warn("EVENT: " + evt);
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.warn("CHANNEL-READ-COMPLETE");
        if (bufferedMode && outboundChannel.isActive()) {
            flushedBuffer = true;
            outboundChannel.writeAndFlush(requestInterceptor(ctx, channelBuffer)).addListener(new ChannelFutureListener() {
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

    private ByteBuf requestInterceptor(ChannelHandlerContext ctx, ByteBuf channelBuffer) throws Exception {
        ByteBuf channelBufferCopy = Unpooled.copiedBuffer(channelBuffer);
        try {
            List<ByteBuf> allRequestRawChunks = new ArrayList<ByteBuf>();
            List<Object> requestHttpFormattedChunks = new ArrayList<Object>();
            new HttpRequestDecoder() {
                public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                    super.callDecode(ctx, in, out);
                }
            }.callDecode(ctx, channelBufferCopy, requestHttpFormattedChunks);

            for (Object httpChunk : requestHttpFormattedChunks) {
                if (httpChunk instanceof HttpRequest) {
                    HttpRequest httpRequest = (HttpRequest) httpChunk;
                    httpRequest.headers().remove(HttpHeaders.Names.ACCEPT_ENCODING);
//                    httpRequest.headers().set(HttpHeaders.Names.HOST, "www.mock-server.com");
                    httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
                }
                logger.warn("HTTP-FORMATTED -- " + httpChunk.getClass().getSimpleName() + " -- " + httpChunk);
                if (!(httpChunk instanceof LastHttpContent)) {
                    List<Object> requestRawChunks = new ArrayList<Object>();
                    new HttpRequestEncoder() {
                        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                            super.encode(ctx, msg, out);
                        }
                    }.encode(ctx, httpChunk, requestRawChunks);
                    for (Object rawChunk : requestRawChunks) {
                        if (rawChunk instanceof ByteBuf) {
                            allRequestRawChunks.add((ByteBuf) rawChunk);
                        }
                    }
                }
            }

            return Unpooled.copiedBuffer(allRequestRawChunks.toArray(new ByteBuf[allRequestRawChunks.size()]));
        } finally {
            channelBufferCopy.release();
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            // handle CONNECT request
            HttpRequest httpRequest = (HttpRequest) msg;
            logger.warn("CONNECT" + httpRequest);
            channelActive(ctx);
            ctx.channel().writeAndFlush(Unpooled.copiedBuffer("HTTP/1.1 200 OK", Charsets.UTF_8)).addListeners(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            logger.warn("WRITE_AND_FLUSH_COMPLETE");
                        }
                    }
            );
        } else if (msg instanceof ByteBuf) {
            final ByteBuf chunk = (ByteBuf) msg;
            logger.warn("CHANNEL-READ");
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
