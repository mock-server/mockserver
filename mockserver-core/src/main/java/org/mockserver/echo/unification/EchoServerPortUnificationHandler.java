package org.mockserver.echo.unification;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpUtil.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class EchoServerPortUnificationHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final AttributeKey<Boolean> SSL_ENABLED = AttributeKey.valueOf("SSL_ENABLED");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    EchoServerPortUnificationHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // Will use the first five bytes to detect a protocol.
        if (msg.readableBytes() < 3) {
            return;
        }

        if (isSsl(msg)) {
            enableSsl(ctx, msg);
        } else if (isHttp(msg)) {
            switchToHttp(ctx, msg);
        } else {
            // Unknown protocol; discard everything and close the connection.
            msg.clear();
            ctx.close();
        }

        if (logger.isTraceEnabled()) {
            if (ctx.pipeline().get(org.mockserver.logging.LoggingHandler.class) != null) {
                ctx.pipeline().remove(org.mockserver.logging.LoggingHandler.class);
            }
            if (ctx.pipeline().get(SslHandler.class) != null) {
                ctx.pipeline().addAfter("SslHandler#0", "LoggingHandler#0", new org.mockserver.logging.LoggingHandler(logger));
            } else {
                ctx.pipeline().addFirst(new org.mockserver.logging.LoggingHandler(logger));
            }
        }
    }

    private boolean isSsl(ByteBuf buf) {
        return buf.readableBytes() >= 5 && SslHandler.isEncrypted(buf);
    }

    private boolean isHttp(ByteBuf msg) {
        int letterOne = (int) msg.getUnsignedByte(msg.readerIndex());
        int letterTwo = (int) msg.getUnsignedByte(msg.readerIndex() + 1);
        int letterThree = (int) msg.getUnsignedByte(msg.readerIndex() + 2);
        return letterOne == 'G' && letterTwo == 'E' && letterThree == 'T' ||  // GET
            letterOne == 'P' && letterTwo == 'O' && letterThree == 'S' || // POST
            letterOne == 'P' && letterTwo == 'U' && letterThree == 'T' || // PUT
            letterOne == 'H' && letterTwo == 'E' && letterThree == 'A' || // HEAD
            letterOne == 'O' && letterTwo == 'P' && letterThree == 'T' || // OPTIONS
            letterOne == 'P' && letterTwo == 'A' && letterThree == 'T' || // PATCH
            letterOne == 'D' && letterTwo == 'E' && letterThree == 'L' || // DELETE
            letterOne == 'T' && letterTwo == 'R' && letterThree == 'A' || // TRACE
            letterOne == 'C' && letterTwo == 'O' && letterThree == 'N';   // CONNECT
    }

    private void enableSsl(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addFirst(nettySslContextFactory().createServerSslContext().newHandler(ctx.alloc()));
        ctx.channel().attr(SSL_ENABLED).set(Boolean.TRUE);

        // re-unify (with SSL enabled)
        ctx.pipeline().fireChannelRead(msg);
    }

    private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec(8192, 8192, 8192));
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));
        if (logger.isDebugEnabled()) {
            addLastIfNotPresent(pipeline, new io.netty.handler.logging.LoggingHandler());
        }
        configurePipeline(pipeline);
        pipeline.remove(this);

        // fire message back through pipeline
        ctx.fireChannelRead(msg);
    }

    private void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.get(channelHandler.getClass()) == null) {
            pipeline.addLast(channelHandler);
        }
    }

    private void configurePipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {

            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                HttpResponseStatus responseStatus = OK;
                if (request.uri().equals("/not_found")) {
                    responseStatus = NOT_FOUND;
                }
                // echo back request headers and body
                FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, responseStatus, Unpooled.copiedBuffer(request.content()));
                response.headers().add(request.headers());

                // set hop-by-hop headers
                response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
                if (isKeepAlive(request)) {
                    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                }
                if (is100ContinueExpected(request)) {
                    ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                }

                // write and flush
                ctx.writeAndFlush(response);
            }

            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                cause.printStackTrace();
                ctx.close();
            }
        });
    }
}
