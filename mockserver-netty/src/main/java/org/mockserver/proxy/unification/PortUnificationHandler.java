package org.mockserver.proxy.unification;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksProtocolVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.socks.SocksProxyHandler;
import org.mockserver.socket.NettySslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.proxy.error.Logging.shouldIgnoreException;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public abstract class PortUnificationHandler extends SimpleChannelInboundHandler<ByteBuf> {

    public static final AttributeKey<Boolean> SSL_ENABLED_UPSTREAM = AttributeKey.valueOf("PROXY_SSL_ENABLED_UPSTREAM");
    public static final AttributeKey<Boolean> SSL_ENABLED_DOWNSTREAM = AttributeKey.valueOf("SSL_ENABLED_DOWNSTREAM");

    @VisibleForTesting
    public static Logger logger = LoggerFactory.getLogger(PortUnificationHandler.class);

    public PortUnificationHandler() {
        super(false);
    }

    public static void enabledSslUpstreamAndDownstream(Channel channel) {
        channel.attr(PortUnificationHandler.SSL_ENABLED_UPSTREAM).set(Boolean.TRUE);
        channel.attr(PortUnificationHandler.SSL_ENABLED_DOWNSTREAM).set(Boolean.TRUE);
    }

    public static boolean isSslEnabledUpstream(Channel channel) {
        if (channel.attr(SSL_ENABLED_UPSTREAM).get() != null) {
            return channel.attr(SSL_ENABLED_UPSTREAM).get();
        } else {
            return false;
        }
    }

    public static void enabledSslDownstream(Channel channel) {
        channel.attr(PortUnificationHandler.SSL_ENABLED_DOWNSTREAM).set(Boolean.TRUE);
    }

    public static void disableSslDownstream(Channel channel) {
        channel.attr(PortUnificationHandler.SSL_ENABLED_DOWNSTREAM).set(Boolean.FALSE);
    }

    public static boolean isSslEnabledDownstream(Channel channel) {
        if (channel.attr(SSL_ENABLED_DOWNSTREAM).get() != null) {
            return channel.attr(SSL_ENABLED_DOWNSTREAM).get();
        } else {
            return false;
        }
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // Will use the first five bytes to detect a protocol.
        if (msg.readableBytes() < 3) {
            return;
        }

        if (isSsl(msg)) {
            enableSsl(ctx, msg);
        } else if (isSocks(msg)) {
            enableSocks(ctx, msg);
        } else if (isHttp(msg)) {
            switchToHttp(ctx, msg);
        } else {
            // Unknown protocol; discard everything and close the connection.
            msg.clear();
            ctx.close();
        }

        if (logger.isTraceEnabled()) {
            if (ctx.pipeline().get(LoggingHandler.class) != null) {
                ctx.pipeline().remove(LoggingHandler.class);
            }
            if (ctx.pipeline().get(SslHandler.class) != null) {
                ctx.pipeline().addAfter("SslHandler#0", "LoggingHandler#0", new LoggingHandler(logger));
            } else {
                ctx.pipeline().addFirst(new LoggingHandler(logger));
            }
        }
    }

    private boolean isSsl(ByteBuf buf) {
        return buf.readableBytes() >= 5 && SslHandler.isEncrypted(buf);
    }

    private boolean isSocks(ByteBuf msg) {
        switch (SocksProtocolVersion.valueOf(msg.getByte(msg.readerIndex()))) {
            case SOCKS5:
            case SOCKS4a:
                break;
            default:
                return false;
        }

        byte numberOfAuthenticationMethods = msg.getByte(msg.readerIndex() + 1);
        for (int i = 0; i < numberOfAuthenticationMethods; i++) {
            switch (SocksAuthScheme.valueOf(msg.getByte(msg.readerIndex() + 1 + i))) {
                case NO_AUTH:
                case AUTH_PASSWORD:
                case AUTH_GSSAPI:
                    break;
                default:
                    return false;
            }
        }
        return true;
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

        // re-unify (with SSL enabled)
        PortUnificationHandler.enabledSslUpstreamAndDownstream(ctx.channel());
        ctx.pipeline().fireChannelRead(msg);
    }

    private void enableSocks(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addFirst(new SocksProxyHandler());
        pipeline.addFirst(new SocksMessageEncoder());
        pipeline.addFirst(new SocksInitRequestDecoder());

        // re-unify (with SOCKS enabled)
        ctx.pipeline().fireChannelRead(msg);
    }

    private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec(8192, 8192, 8192));
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));

        configurePipeline(ctx, pipeline);
        pipeline.remove(this);

        // pass message to next stage in pipeline
        ctx.fireChannelRead(msg);
    }

    protected void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.get(channelHandler.getClass()) == null) {
            pipeline.addLast(channelHandler);
        }
    }

    protected abstract void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            logger.warn("Exception caught by port unification handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }
}
