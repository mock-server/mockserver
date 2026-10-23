package org.mockserver.netty.unification;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.mockserver.mock.action.http.TcpChaosRegistry;
import org.mockserver.model.TcpChaosProfile;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.ConnectionClosing;
import org.mockserver.socket.ConnectionReset;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Netty {@link ChannelDuplexHandler} that injects raw TCP-layer faults
 * <em>before</em> HTTP decoding, based on the {@link TcpChaosProfile}
 * registered in {@link TcpChaosRegistry} for the connection's remote host.
 *
 * <p>Fault types mirror Toxiproxy's named toxics: latency, down, bandwidth,
 * slow_close, timeout, reset_peer, slicer, limit_data.
 *
 * <p>Latency and bandwidth model a slow link: inbound reads join one FIFO queue per
 * connection and are delivered in arrival order, each after the bytes ahead of it have been
 * sent at the configured bandwidth and then the configured latency has elapsed. While more than
 * {@link #PAUSE_READS_ABOVE_QUEUED_BYTES} are queued the connection stops reading (a
 * {@link ChannelReadPause} hold), resuming at {@link #RESUME_READS_AT_QUEUED_BYTES}, so a fast
 * sender is held back by TCP flow control, as behind a real slow link, instead of queueing without
 * limit. Queued buffers are released when the connection closes.
 *
 * <p>This handler is <em>not</em> sharable ({@code @ChannelHandler.Sharable}
 * is intentionally absent) because it maintains per-connection state.
 */
public class TcpChaosHandler extends ChannelDuplexHandler {

    static final int PAUSE_READS_ABOVE_QUEUED_BYTES = 64 * 1024;
    static final int RESUME_READS_AT_QUEUED_BYTES = PAUSE_READS_ABOVE_QUEUED_BYTES / 2;

    private static final class QueuedRead {
        private final Object msg;
        private final long deliverAtNanos;
        private final int bytes;

        private QueuedRead(Object msg, long deliverAtNanos, int bytes) {
            this.msg = msg;
            this.deliverAtNanos = deliverAtNanos;
            this.bytes = bytes;
        }
    }

    private final Function<Channel, TcpChaosProfile> profiles;
    private long bytesConsumed = 0;
    private final ArrayDeque<QueuedRead> queue = new ArrayDeque<>();
    private long queuedBytes;
    private long lastDepartureNanos = Long.MIN_VALUE;
    private long lastDeliverAtNanos = Long.MIN_VALUE;
    private ScheduledFuture<?> drainTimer;
    private boolean readsPaused;

    public TcpChaosHandler() {
        this(TcpChaosHandler::registeredProfile);
    }

    TcpChaosHandler(Function<Channel, TcpChaosProfile> profiles) {
        this.profiles = profiles;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TcpChaosProfile profile = getProfile(ctx);
        if (profile == null || !profile.hasAnyFault()) {
            forward(ctx, msg, 0, 0);
            return;
        }

        // down: drop ALL incoming data silently
        if (Boolean.TRUE.equals(profile.getDown())) {
            ReferenceCountUtil.release(msg);
            return;
        }

        // reset peer: send RST and close immediately (no TLS close_notify; a tunnel's client is reset too)
        if (Boolean.TRUE.equals(profile.getResetPeer())) {
            ReferenceCountUtil.release(msg);
            ConnectionReset.reset(ctx.channel());
            return;
        }

        // limitData: close the connection after consuming enough bytes
        if (profile.getLimitDataBytes() != null && profile.getLimitDataBytes() > 0) {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                if (bytesConsumed >= profile.getLimitDataBytes()) {
                    buf.release();
                    // this close starts beneath the pipelining gate, so the gate is told first
                    ConnectionClosing.mark(ctx.channel());
                    ctx.close();
                    return;
                }
                bytesConsumed += buf.readableBytes();
            }
        }

        // slicer: fragment the buffer into small chunks
        if (profile.getSlicerChunkSize() != null && profile.getSlicerChunkSize() > 0
            && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int chunkSize = profile.getSlicerChunkSize();
            while (buf.readableBytes() > 0) {
                int len = Math.min(chunkSize, buf.readableBytes());
                forward(ctx, buf.readRetainedSlice(len), 0, 0);
            }
            buf.release();
            return;
        }

        long transmitNanos = 0;
        if (profile.getBandwidthBytesPerSec() != null && profile.getBandwidthBytesPerSec() > 0
            && msg instanceof ByteBuf) {
            transmitNanos = (((ByteBuf) msg).readableBytes() * 1_000_000_000L) / profile.getBandwidthBytesPerSec();
        }
        long latencyNanos = profile.getLatencyMs() != null && profile.getLatencyMs() > 0
            ? TimeUnit.MILLISECONDS.toNanos(profile.getLatencyMs()) : 0;
        forward(ctx, msg, transmitNanos, latencyNanos);
    }

    /**
     * Pass {@code msg} on at once when nothing is queued and no delay applies; otherwise queue it behind the
     * reads already queued, so bytes are never reordered.
     */
    private void forward(ChannelHandlerContext ctx, Object msg, long transmitNanos, long latencyNanos) {
        if (transmitNanos == 0 && latencyNanos == 0 && queue.isEmpty()) {
            ctx.fireChannelRead(msg);
            return;
        }
        long now = ctx.executor().ticker().nanoTime();
        long departure = Math.max(now, lastDepartureNanos) + transmitNanos;
        lastDepartureNanos = departure;
        long deliverAt = Math.max(departure + latencyNanos, lastDeliverAtNanos);
        lastDeliverAtNanos = deliverAt;
        int bytes = msg instanceof ByteBuf ? ((ByteBuf) msg).readableBytes() : 0;
        queue.add(new QueuedRead(msg, deliverAt, bytes));
        queuedBytes += bytes;
        if (!readsPaused && queuedBytes > PAUSE_READS_ABOVE_QUEUED_BYTES) {
            readsPaused = true;
            ChannelReadPause.pause(ctx.channel());
        }
        scheduleDrain(ctx);
    }

    private void scheduleDrain(ChannelHandlerContext ctx) {
        QueuedRead head = queue.peek();
        if (drainTimer == null && head != null) {
            drainTimer = ctx.executor().schedule(() -> drain(ctx), head.deliverAtNanos - ctx.executor().ticker().nanoTime(), TimeUnit.NANOSECONDS);
        }
    }

    private void drain(ChannelHandlerContext ctx) {
        drainTimer = null;
        long now = ctx.executor().ticker().nanoTime();
        boolean delivered = false;
        while (!ctx.isRemoved() && !queue.isEmpty() && queue.peek().deliverAtNanos - now <= 0) {
            QueuedRead read = queue.poll();
            queuedBytes -= read.bytes;
            delivered = true;
            ctx.fireChannelRead(read.msg);
        }
        if (delivered && !ctx.isRemoved()) {
            ctx.fireChannelReadComplete();
        }
        if (readsPaused && queuedBytes <= RESUME_READS_AT_QUEUED_BYTES) {
            resumeReads(ctx);
        }
        if (!ctx.isRemoved()) {
            scheduleDrain(ctx);
        }
    }

    private void resumeReads(ChannelHandlerContext ctx) {
        readsPaused = false;
        ChannelReadPause.resume(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        discardQueue();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (drainTimer != null) {
            drainTimer.cancel(false);
            drainTimer = null;
        }
        if (ctx.channel().isActive()) {
            // this handler can no longer delay them: pass the queued reads on, in order
            QueuedRead read;
            while ((read = queue.poll()) != null) {
                ctx.fireChannelRead(read.msg);
            }
            queuedBytes = 0;
        } else {
            discardQueue();
        }
        if (readsPaused) {
            resumeReads(ctx);
        }
    }

    private void discardQueue() {
        if (drainTimer != null) {
            drainTimer.cancel(false);
            drainTimer = null;
        }
        QueuedRead read;
        while ((read = queue.poll()) != null) {
            ReferenceCountUtil.release(read.msg);
        }
        queuedBytes = 0;
    }

    long queuedBytes() {
        return queuedBytes;
    }

    boolean readsPaused() {
        return readsPaused;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        TcpChaosProfile profile = getProfile(ctx);
        if (profile != null && Boolean.TRUE.equals(profile.getSlowClose())) {
            // Delay the actual close by 2 seconds to simulate slow FIN
            ctx.channel().eventLoop().schedule(
                () -> ctx.close(promise),
                2000, TimeUnit.MILLISECONDS
            );
            return;
        }
        if (profile != null && Boolean.TRUE.equals(profile.getTimeout())) {
            // Timeout: never send FIN, just silently drop the close
            promise.setSuccess();
            return;
        }
        super.close(ctx, promise);
    }

    private TcpChaosProfile getProfile(ChannelHandlerContext ctx) {
        return profiles.apply(ctx.channel());
    }

    private static TcpChaosProfile registeredProfile(Channel channel) {
        if (channel.remoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) channel.remoteAddress();
            return TcpChaosRegistry.getInstance().get(addr.getHostString());
        }
        return null;
    }
}
