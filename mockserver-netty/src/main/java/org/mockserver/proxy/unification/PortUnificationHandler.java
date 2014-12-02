package org.mockserver.proxy.unification;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
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
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.proxy.http.HttpProxyHandler;
import org.mockserver.proxy.socks.SocksProxyHandler;
import org.mockserver.socket.SSLFactory;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public abstract class PortUnificationHandler extends SimpleChannelInboundHandler<ByteBuf> {

    public static final AttributeKey<Boolean> SSL_ENABLED = AttributeKey.valueOf("PROXY_SSL_ENABLED");

    public PortUnificationHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // Will use the first five bytes to detect a protocol.
        if (msg.readableBytes() < 4) {
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
        pipeline.addFirst(new SslHandler(SSLFactory.createServerSSLEngine()));

        // re-unify (with SSL enabled)
        ctx.channel().attr(SSL_ENABLED).set(Boolean.TRUE);
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
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpContentDecompressor());
        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
        configurePipeline(ctx, pipeline);
        pipeline.remove(this);

        // fire message back through pipeline
        ctx.fireChannelRead(msg);
    }

    protected abstract void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline);

    public static boolean isSslEnabled(ChannelHandlerContext ctx) {
        if (ctx.channel().attr(SSL_ENABLED).get() != null) {
            return ctx.channel().attr(SSL_ENABLED).get();
        } else {
            return false;
        }
    }
}
