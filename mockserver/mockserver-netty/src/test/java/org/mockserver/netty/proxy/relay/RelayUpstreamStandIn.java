package org.mockserver.netty.proxy.relay;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.mockserver.httpclient.NettyHttpClient;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Stands in for a binary relay's upstream connection, for a test about what happens on MockServer's side of a binary
 * connection: each upstream connection is an embedded channel, connected at once, that takes everything and never
 * answers, and its TLS handler counts its handshake as done at once, so the relay never holds the client back for
 * either.
 */
public final class RelayUpstreamStandIn {

    private final List<EmbeddedChannel> upstreams = new CopyOnWriteArrayList<>();
    // what the relay wrote, in order, each with whether a TLS handler was in place to encrypt it
    private final List<Write> writes = new CopyOnWriteArrayList<>();

    /**
     * @param httpClient a mock, whose relay connections this answers
     */
    public RelayUpstreamStandIn(NettyHttpClient httpClient) {
        when(httpClient.connectBinaryRelay(any(EventLoop.class), any(InetSocketAddress.class), anyBoolean(), any(ChannelHandler.class))).thenAnswer(invocation -> {
            EmbeddedChannel upstream = new EmbeddedChannel(invocation.<ChannelHandler>getArgument(3), new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    // an empty write is how a leg is flushed before its output is ended, not bytes sent
                    if (msg instanceof ByteBuf && ((ByteBuf) msg).isReadable()) {
                        ByteBuf bytes = (ByteBuf) msg;
                        writes.add(new Write(ByteBufUtil.getBytes(bytes, bytes.readerIndex(), bytes.readableBytes()), ctx.pipeline().get(SslHandler.class) != null));
                    }
                    super.write(ctx, msg, promise);
                }
            });
            // what the TLS handler keeps counts against writability, and a stand-in never stops taking bytes
            upstream.config().setWriteBufferWaterMark(new WriteBufferWaterMark(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
            upstreams.add(upstream);
            return upstream.newSucceededFuture();
        });
        when(httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenAnswer(invocation -> handshakeDoneAtOnce(invocation.getArgument(0)));
    }

    private static SslHandler handshakeDoneAtOnce(ByteBufAllocator allocator) throws SSLException {
        return new SslHandler(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(SslProvider.JDK).build().newEngine(allocator)) {
            @Override
            public Future<Channel> handshakeFuture() {
                return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
            }
        };
    }

    public List<EmbeddedChannel> upstreams() {
        return upstreams;
    }

    /**
     * What the relay has written to its upstream connections in the clear since this was last called, one entry for
     * each write; what it wrote once a TLS handler was in place is left out.
     */
    public List<byte[]> writtenInTheClear() {
        List<byte[]> clear = new ArrayList<>();
        for (Write write : writes) {
            if (!write.overTls) {
                clear.add(write.bytes);
            }
        }
        writes.clear();
        for (EmbeddedChannel upstream : upstreams) {
            for (Object sent; (sent = upstream.readOutbound()) != null; ) {
                ReferenceCountUtil.release(sent);
            }
        }
        return clear;
    }

    /** Releases everything every upstream connection still holds. */
    public void releaseAll() {
        for (EmbeddedChannel upstream : upstreams) {
            if (upstream.isRegistered()) {
                upstream.finishAndReleaseAll();
            } else {
                upstream.releaseInbound();
                upstream.releaseOutbound();
            }
        }
    }

    private static final class Write {
        private final byte[] bytes;
        private final boolean overTls;

        private Write(byte[] bytes, boolean overTls) {
            this.bytes = bytes;
            this.overTls = overTls;
        }
    }
}
