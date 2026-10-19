package org.mockserver.netty.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.SegmentedDatagramPacket;
import io.netty.handler.codec.quic.SegmentedDatagramPacketAllocator;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;

/**
 * The HTTP/3 listener's segmented (GSO, {@code UDP_SEGMENT}) sends, given to the QUIC codec as its allocator and
 * placed in the listener's pipeline to watch the sends it makes. The kernel can refuse a segmented send on one path
 * while it supports the option (EIO through a device without checksum offload, such as a TUN device before Linux
 * 6.11, or an IPsec route), and Netty then drops the packets with no error anyone sees. After the first failed
 * segmented send, whatever its cause (a closed listener aside), it reports no segments for the listener's life, so the
 * codec sends one datagram per packet, and QUIC resends what was lost as it does any loss. One instance serves one
 * listener, and its sends all complete on that listener's loop.
 */
public class Http3SegmentedSends extends ChannelOutboundHandlerAdapter implements SegmentedDatagramPacketAllocator {

    private final SegmentedDatagramPacketAllocator segmented;
    private final MockServerLogger mockServerLogger;
    private volatile boolean refused;

    public Http3SegmentedSends(SegmentedDatagramPacketAllocator segmented, MockServerLogger mockServerLogger) {
        this.segmented = segmented;
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    public int maxNumSegments() {
        return refused ? 0 : segmented.maxNumSegments();
    }

    @Override
    public DatagramPacket newPacket(ByteBuf buffer, int segmentSize, InetSocketAddress remoteAddress) {
        return segmented.newPacket(buffer, segmentSize, remoteAddress);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (refused || !(msg instanceof SegmentedDatagramPacket)) {
            ctx.write(msg, promise);
            return;
        }
        ChannelPromise sent = promise.unvoid();
        sent.addListener(future -> {
            if (!future.isSuccess()) {
                segmentedSendFailed(ctx.channel(), future.cause());
            }
        });
        ctx.write(msg, sent);
    }

    private void segmentedSendFailed(Channel listener, Throwable cause) {
        if (refused || cause instanceof ClosedChannelException || !listener.isActive()) {
            return;
        }
        refused = true;
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.WARN)
                .setMessageFormat("segmented (GSO) send failed on HTTP/3 listener on:{}:{}:{}; sending one datagram per packet"
                    + " from now on, as QUIC resends what was lost; set useNativeTransport=false to use the NIO transport")
                .setArguments(listener.localAddress(), cause.getClass().getName(), cause.getMessage())
        );
    }
}
