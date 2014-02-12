package org.mockserver.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksAuthScheme;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksProtocolVersion;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.socket.SSLFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ProxyUnificationHandler extends ByteToMessageDecoder {

    private final boolean sslEnabled;
    private final boolean socksEnabled;
    private final LogFilter logFilter = new LogFilter();
    private final int port;

    public ProxyUnificationHandler(int port) {
        this(true, true, port);
    }

    private ProxyUnificationHandler(boolean sslEnabled, boolean socksEnabled, int port) {
        this.sslEnabled = sslEnabled;
        this.socksEnabled = socksEnabled;
        this.port = port;
    }


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        // Will use the first five bytes to detect a protocol.
        if (msg.readableBytes() < 5) {
            return;
        }

        if (isSsl(msg)) {
            enableSsl(ctx);
        } else if (isSocks(msg)) {
            enableSocks(ctx);
        } else if (isHttp(msg)) {
            switchToHttp(ctx);
        } else {
            // Unknown protocol; discard everything and close the connection.
            msg.clear();
            ctx.close();
        }

    }

    private boolean isSsl(ByteBuf buf) {
        return sslEnabled && SslHandler.isEncrypted(buf);
    }

    private boolean isSocks(ByteBuf msg) {
        if (socksEnabled) {
            switch (SocksProtocolVersion.fromByte(msg.getByte(msg.readerIndex()))) {
                case SOCKS5:
                case SOCKS4a:
                    break;
                default:
                    return false;
            }

            byte numberOfAuthenticationMethods = msg.getByte(msg.readerIndex() + 1);
            for (int i = 0; i < numberOfAuthenticationMethods; i++) {
                switch (SocksAuthScheme.fromByte(msg.getByte(msg.readerIndex() + 1 + i))) {
                    case NO_AUTH:
                    case AUTH_PASSWORD:
                    case AUTH_GSSAPI:
                        break;
                    default:
                        return false;
                }
            }
        }
        return false;
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

    private void enableSsl(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
        engine.setUseClientMode(false);
        pipeline.addLast("ssl", new SslHandler(engine));

        // re-unify
        pipeline.addLast("sslUnification", new ProxyUnificationHandler(false, socksEnabled, port));
        pipeline.remove(this);
    }

    private void enableSocks(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast(SocksInitRequestDecoder.class.getSimpleName(), new SocksInitRequestDecoder());
        pipeline.addLast(SocksMessageEncoder.class.getSimpleName(), new SocksMessageEncoder());
        pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, null, new InetSocketAddress(port), sslEnabled));

        // re-unify
        pipeline.addLast("socksUnification", new ProxyUnificationHandler(sslEnabled, false, port));
        pipeline.remove(this);
    }

    private void switchToHttp(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());
        pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, null, new InetSocketAddress(port), sslEnabled));
        pipeline.remove(this);
    }
}
