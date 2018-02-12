package org.mockserver.dashboard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.unification.HttpContentLengthRemover;
import org.mockserver.unification.PortUnificationHandler;
import org.slf4j.LoggerFactory;

import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DashboardUnificationInitializer extends SimpleChannelInboundHandler<ByteBuf> {
    private final MockServerLogger mockServerLogger = new MockServerLogger(DashboardUnificationInitializer.class);
    private final LoggingHandler loggingHandler = new LoggingHandler(LoggerFactory.getLogger(PortUnificationHandler.class));
    private final HttpContentLengthRemover httpContentLengthRemover = new HttpContentLengthRemover();

    public DashboardUnificationInitializer() {
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

        if (mockServerLogger.isEnabled(TRACE)) {
            loggingHandler.addLoggingHandler(ctx);
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
        PortUnificationHandler.enabledSslUpstreamAndDownstream(ctx.channel());

        // re-unify (with SSL enabled)
        ctx.pipeline().fireChannelRead(msg);
    }

    private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec(8192, 8192, 8192));
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, httpContentLengthRemover);
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));

        if (mockServerLogger.isEnabled(TRACE)) {
            addLastIfNotPresent(pipeline, loggingHandler);
        }
        pipeline.addLast(new DashboardHandler());
        pipeline.remove(this);

        // fire message back through pipeline
        ctx.fireChannelRead(msg);
    }

    private void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.get(channelHandler.getClass()) == null) {
            pipeline.addLast(channelHandler);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.error("Exception caught by dashboard unification handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }

}
