package org.mockserver.server.unification;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.mockserver.socket.NettySslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public abstract class PortUnificationHandler extends SimpleChannelInboundHandler<ByteBuf> {

    public static final AttributeKey<Boolean> SSL_ENABLED = AttributeKey.valueOf("SSL_ENABLED");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public PortUnificationHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
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
        ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).set(Boolean.TRUE);

        // re-unify (with SSL enabled)
        ctx.pipeline().fireChannelRead(msg);
    }

    private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec());
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, new HttpContentLengthRemover());
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));
        if (logger.isDebugEnabled()) {
            addLastIfNotPresent(pipeline, new LoggingHandler());
        }
        configurePipeline(ctx, pipeline);
        pipeline.remove(this);

        // fire message back through pipeline
        ctx.fireChannelRead(msg);
    }

    protected void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.get(channelHandler.getClass()) == null) {
            pipeline.addLast(channelHandler);
        }
    }

    protected abstract void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline);
}
