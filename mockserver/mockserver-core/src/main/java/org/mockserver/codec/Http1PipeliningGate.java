package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.ConnectionClosing;
import org.mockserver.socket.ConnectionClosingEvent;
import org.mockserver.socket.ReadAfterFailedWrite;
import org.slf4j.event.Level;

import java.util.ArrayDeque;
import java.util.Iterator;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Answers pipelined HTTP/1.1 requests in the order they arrived, by passing at most one exchange at a time to the
 * handlers after it. A request decoded while another is outstanding is held, with its body, and released one event-loop
 * task after the earlier exchange's response has been written (or the exchange has otherwise ended), unless the
 * connection is closing; held requests then wait for {@code channelInactive}. No response is ever buffered.
 * <p>
 * One instance per connection, after {@code HttpServerCodec} and the handlers that pair requests with responses, and
 * before every handler that aggregates or answers; every field is confined to the event loop. A connection that never
 * pipelines takes only the hot path: a few type checks and field stores per message, and no allocation.
 * <p>
 * Held requests are bounded by cost (128 bytes per held object plus each distinct read buffer they pin, at most
 * {@code maxRequestBodySize} before reads pause), by count ({@value #MAX_HELD_REQUESTS}; past it the connection
 * answers what it holds and closes), and by time: held requests behind an exchange whose response makes no progress
 * for {@code inboundConnectionIdleTimeoutMillis} close the connection.
 */
public final class Http1PipeliningGate extends ChannelDuplexHandler {

    static final int MAX_HELD_REQUESTS = 1024;
    // at least the heap of a held content object, its derived slice and its deque slot
    static final long OBJECT_COST = 128;
    static final long MAX_BACKSTOP_TICK_MILLIS = 10_000;
    static final long MIN_BACKSTOP_TICK_MILLIS = 10;
    static final long DISABLED_BACKSTOP_REPORT_MILLIS = 300_000;

    private final Configuration configuration;
    private final HeldRequestAccounting accounting;
    private final MockServerLogger mockServerLogger;
    private final Runnable wedgedCounter;
    private final long heldCostLimit;
    private final long backstopMillis;
    private final long backstopTickMillis;
    // created on first slow-path use, so a connection that never pipelines does not pay for them
    private Runnable drainTask;
    private ChannelFutureListener afterTerminalWrite;
    private Runnable backstopTask;

    private ChannelHandlerContext ctx;
    private boolean outstanding;
    private boolean awaitingWrite;
    private boolean bodyArriving;
    private boolean slowPath;
    private boolean closing;
    private boolean capped;
    private boolean paused;
    private boolean drainScheduled;
    private boolean switching;
    private boolean droppedWhileClosing;
    private boolean interimHead;
    private boolean removed;
    private boolean releasing;
    private boolean wedgeReported;
    private HttpResponse pendingHead;
    private ArrayDeque<Object> held;
    private int heldRequests;
    private long heldCost;
    // compared by identity only, never dereferenced; non-null only while a held object retains it
    private ByteBuf lastPinned;
    private int openHeldTokens;
    private int progress;
    private int progressAtLastTick;
    private long stalledMillis;
    private ScheduledFuture<?> backstop;

    public Http1PipeliningGate(Configuration configuration, HeldRequestAccounting accounting, MockServerLogger mockServerLogger) {
        this(configuration, accounting, mockServerLogger, Metrics::incrementHttp1PipeliningWedgedConnections);
    }

    Http1PipeliningGate(Configuration configuration, HeldRequestAccounting accounting, MockServerLogger mockServerLogger, Runnable wedgedCounter) {
        this.configuration = configuration;
        this.accounting = accounting;
        this.mockServerLogger = mockServerLogger;
        this.heldCostLimit = configuration.maxRequestBodySize();
        this.backstopMillis = configuration.inboundConnectionIdleTimeoutMillis();
        this.backstopTickMillis = backstopTickMillis(backstopMillis);
        this.wedgedCounter = wedgedCounter;
    }

    /**
     * A tenth of the backstop time, so the backstop fires within about 10% of the idle timeout, however short.
     */
    static long backstopTickMillis(long backstopMillis) {
        if (backstopMillis <= 0) {
            return MAX_BACKSTOP_TICK_MILLIS;
        }
        return Math.max(MIN_BACKSTOP_TICK_MILLIS, Math.min(MAX_BACKSTOP_TICK_MILLIS, backstopMillis / 10));
    }

    /**
     * Stops releasing held requests for good: the connection is becoming a CONNECT tunnel, and anything held is bytes
     * of the tunnel that {@link #removeFrom} drops. Call on the connection's event loop.
     */
    public static void switching(ChannelPipeline pipeline) {
        Http1PipeliningGate gate = pipeline.get(Http1PipeliningGate.class);
        if (gate != null) {
            gate.switching = true;
            gate.settle();
        }
    }

    /**
     * Removes the gate, if present, for when the connection stops carrying HTTP/1.1; anything held is dropped.
     */
    public static void removeFrom(ChannelPipeline pipeline) {
        if (pipeline.get(Http1PipeliningGate.class) != null) {
            pipeline.remove(Http1PipeliningGate.class);
        }
    }

    /**
     * Test hook: the cost of what the connection's gate holds (0 without a gate). Read on the event loop.
     */
    public static long heldCost(Channel channel) {
        Http1PipeliningGate gate = channel.pipeline().get(Http1PipeliningGate.class);
        return gate != null ? gate.heldCost : 0;
    }

    /**
     * Test hook: whether the connection's gate takes its slow path for the next message. Read on the event loop.
     */
    public static boolean isSlowPath(Channel channel) {
        Http1PipeliningGate gate = channel.pipeline().get(Http1PipeliningGate.class);
        return gate != null && gate.slowPath;
    }

    /**
     * Test hook: whether the connection's gate has refused requests past {@value #MAX_HELD_REQUESTS} held.
     */
    public static boolean capHit(Channel channel) {
        Http1PipeliningGate gate = channel.pipeline().get(Http1PipeliningGate.class);
        return gate != null && gate.capped;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!slowPath) {
            if (!(msg instanceof HttpRequest)) {
                ctx.fireChannelRead(msg);
                return;
            }
            if (!outstanding) {
                outstanding = true;
                ctx.fireChannelRead(msg);
                return;
            }
        }
        slowRead(ctx, msg);
    }

    private void slowRead(ChannelHandlerContext ctx, Object msg) {
        if (capped || droppedWhileClosing) {
            // every object after the cap or a drop while closing belongs to a request that will not be answered
            ReferenceCountUtil.release(msg);
            return;
        }
        boolean head = msg instanceof HttpRequest;
        if (heldIsEmpty()) {
            if (!head) {
                // the rest of the request already released
                ctx.fireChannelRead(msg);
                if (msg instanceof LastHttpContent && bodyArriving) {
                    bodyArriving = false;
                    closeToken();
                    settle();
                }
                return;
            }
            if (closing && !outstanding && !awaitingWrite && !switching && !isOutputShutdown(ctx.channel())) {
                // not pipelined: answered as it would be without the gate, for example during a delayed close
                outstanding = true;
                ctx.fireChannelRead(msg);
                return;
            }
        }
        hold(msg, head);
    }

    private void hold(Object msg, boolean head) {
        if (head) {
            if (heldRequests >= MAX_HELD_REQUESTS) {
                capped = true;
                ReferenceCountUtil.release(msg);
                warnCapped();
                settle();
                return;
            }
            if (heldIsEmpty()) {
                armBackstop();
            }
            heldRequests++;
            openHeldTokens++;
            accounting.held();
        }
        if (held == null) {
            held = new ArrayDeque<>();
        }
        held.addLast(msg);
        charge(msg);
        if (closing) {
            if (heldCost >= heldCostLimit) {
                dropNewestIncomplete();
            }
        } else if (msg instanceof LastHttpContent || heldCost >= heldCostLimit) {
            pauseOnce();
        }
        settle();
    }

    private void charge(Object msg) {
        heldCost += OBJECT_COST;
        if (msg instanceof HttpContent) {
            ByteBuf root = ((HttpContent) msg).content();
            for (ByteBuf unwrapped = root.unwrap(); unwrapped != null; unwrapped = root.unwrap()) {
                root = unwrapped;
            }
            if (root != lastPinned) {
                heldCost += root.capacity();
                lastPinned = root;
            }
        }
    }

    /**
     * Past the cost limit while closing, where reads are not paused: drops the newest held request if incomplete, and
     * everything after it.
     */
    private void dropNewestIncomplete() {
        Iterator<Object> newestFirst = held.descendingIterator();
        while (newestFirst.hasNext()) {
            Object object = newestFirst.next();
            if (object instanceof LastHttpContent) {
                return;
            }
            if (object instanceof HttpRequest) {
                break;
            }
        }
        int droppedObjects = 0;
        Object dropped;
        do {
            dropped = held.pollLast();
            ReferenceCountUtil.release(dropped);
            droppedObjects++;
        } while (!(dropped instanceof HttpRequest));
        heldRequests--;
        closeToken();
        droppedWhileClosing = true;
        lastPinned = null;
        if (heldIsEmpty()) {
            heldEmptied();
        }
        if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.DEBUG)
                    .setMessageFormat("dropped a pipelined request of{}objects, and everything after it, from:{}because the connection is closing and its held requests passed maxRequestBodySize")
                    .setArguments(droppedObjects, ctx.channel().remoteAddress())
            );
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpResponse) {
            int code = ((HttpResponse) msg).status().code();
            if (code < 200) {
                if (code == 101) {
                    ctx.write(msg, promise);
                    ctx.pipeline().remove(this);
                    return;
                }
                interimHead = !(msg instanceof LastHttpContent);
                if (slowPath) {
                    progress++;
                }
                ctx.write(msg, promise);
                return;
            }
            interimHead = false;
            if (!(msg instanceof LastHttpContent)) {
                pendingHead = (HttpResponse) msg;
                if (slowPath) {
                    progress++;
                }
                ctx.write(msg, promise);
                return;
            }
            terminalWrite(ctx, msg, promise, (HttpResponse) msg);
        } else if (msg instanceof LastHttpContent) {
            if (interimHead) {
                interimHead = false;
                if (slowPath) {
                    progress++;
                }
                ctx.write(msg, promise);
                return;
            }
            terminalWrite(ctx, msg, promise, pendingHead);
        } else {
            if (slowPath) {
                progress++;
            }
            ctx.write(msg, promise);
        }
    }

    private void terminalWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, HttpResponse head) {
        outstanding = false;
        pendingHead = null;
        if (slowPath) {
            if (configuration.alwaysCloseSocketConnections() || (head != null && !HttpUtil.isKeepAlive(head)) || ConnectionClosing.isMarked(ctx.channel())) {
                enterClosing();
            }
            awaitingWrite = true;
            progress++;
            if (afterTerminalWrite == null) {
                afterTerminalWrite = this::afterTerminalWrite;
            }
            promise = promise.unvoid();
            promise.addListener(afterTerminalWrite);
        }
        ctx.write(msg, promise);
    }

    private void afterTerminalWrite(ChannelFuture written) {
        awaitingWrite = false;
        if (written.isSuccess()) {
            settle();
        } else if (ReadAfterFailedWrite.isReadingOn(written.channel())) {
            // the failure ended the output, and the channel reads the client's requests to the end of its input so they
            // are still recorded: drop the read pause now, before the output-shutdown event checks for one, and leave
            // held and later requests to channelInactive
            enterClosing();
        } else {
            // a response that failed part-way leaves the connection's framing unknown
            enterClosing();
            if (removed) {
                written.channel().close();
            } else {
                ctx.close();
            }
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        enterClosing();
        ctx.close(promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        enterClosing();
        ctx.disconnect(promise);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof HttpExchangeEndedEvent) {
            outstanding = false;
            if (slowPath) {
                progress++;
                settle();
            }
        } else if (evt instanceof HttpExpectationFailedEvent) {
            // the decoder will not emit the released request's LastHttpContent
            if (bodyArriving) {
                bodyArriving = false;
                closeToken();
                settle();
            }
        } else if (evt == ConnectionClosingEvent.INSTANCE) {
            enterClosing();
        }
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * The release invariant, run after every slow-path state change: a held request with nothing outstanding or
     * awaiting its write always has a release scheduled, unless the connection is closing, switching protocols or
     * discarding at stop. A capped connection that has answered everything it held is closed.
     */
    private void settle() {
        if (removed) {
            return;
        }
        boolean anyHeld = !heldIsEmpty();
        slowPath = anyHeld || bodyArriving || closing || capped;
        boolean idle = !outstanding && !awaitingWrite;
        if (anyHeld && idle && !closing && !switching && !drainScheduled && held.peekFirst() instanceof HttpRequest && !accounting.discardHeldOnClose()) {
            drainScheduled = true;
            if (drainTask == null) {
                drainTask = this::drain;
            }
            ctx.executor().execute(drainTask);
        } else if (capped && !anyHeld && idle && !closing) {
            enterClosing();
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void enterClosing() {
        closing = true;
        resumeOnce();
        settle();
    }

    private void drain() {
        drainScheduled = false;
        if (removed || outstanding || awaitingWrite || switching) {
            return;
        }
        Channel channel = ctx.channel();
        if (closing || accounting.discardHeldOnClose() || !channel.isActive() || ConnectionClosing.isMarked(channel) || isOutputShutdown(channel)) {
            enterClosing();
            return;
        }
        if (heldIsEmpty()) {
            settle();
            return;
        }
        releaseOneRequest();
        if (removed) {
            return;
        }
        ctx.fireChannelReadComplete();
        if (removed) {
            return;
        }
        if (heldIsEmpty()) {
            heldEmptied();
            resumeOnce();
        }
        settle();
    }

    private void releaseOneRequest() {
        HttpRequest head = (HttpRequest) held.pollFirst();
        heldRequests--;
        boolean complete = head instanceof LastHttpContent || heldHoldsLastOfFirst();
        // before the fire: the aggregator can end the body from inside it, with HttpExpectationFailedEvent
        outstanding = true;
        if (complete) {
            // its body has arrived, so a 417 or 413 now would reset the decoder in a later request's body
            head.headers().remove(HttpHeaderNames.EXPECT);
        } else {
            bodyArriving = true;
        }
        releasing = true;
        try {
            ctx.fireChannelRead(head);
            if (removed) {
                return;
            }
            if (head instanceof LastHttpContent) {
                closeToken();
                return;
            }
            for (Object next = held.peekFirst(); next != null && !(next instanceof HttpRequest); next = held.peekFirst()) {
                held.pollFirst();
                ctx.fireChannelRead(next);
                if (removed) {
                    return;
                }
                if (next instanceof LastHttpContent) {
                    closeToken();
                    return;
                }
            }
        } finally {
            releasing = false;
        }
    }

    private boolean heldHoldsLastOfFirst() {
        for (Object object : held) {
            if (object instanceof HttpRequest) {
                return false;
            }
            if (object instanceof LastHttpContent) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelBackstop();
        enterClosing();
        if (!heldIsEmpty()) {
            if (accounting.discardHeldOnClose()) {
                int dropped = heldRequests;
                releaseHeld();
                if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.DEBUG)
                            .setMessageFormat("dropped{}pipelined requests held on connection from:{}as the server stopped before their turn")
                            .setArguments(dropped, ctx.channel().remoteAddress())
                    );
                }
            } else {
                // as a decoder's decodeLast: complete requests are handled, incomplete ones end prematurely
                for (Object object = held.pollFirst(); object != null && !removed; object = held.pollFirst()) {
                    ctx.fireChannelRead(object);
                }
                if (!removed) {
                    ctx.fireChannelReadComplete();
                }
                releaseHeld();
            }
        }
        bodyArriving = false;
        closeAllTokens();
        heldEmptied();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        cancelBackstop();
        if (!heldIsEmpty()) {
            int count = held.size();
            releaseHeld();
            if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("dropped{}objects pipelined on connection from:{}after it switched protocols, as they belong to the new protocol")
                        .setArguments(count, ctx.channel().remoteAddress())
                );
            }
        }
        bodyArriving = false;
        closeAllTokens();
        heldEmptied();
        resumeOnce();
    }

    private void releaseHeld() {
        if (held != null) {
            for (Object object = held.pollFirst(); object != null; object = held.pollFirst()) {
                ReferenceCountUtil.release(object);
            }
        }
        heldRequests = 0;
    }

    private void heldEmptied() {
        lastPinned = null;
        heldCost = 0;
        cancelBackstop();
    }

    private void closeToken() {
        if (openHeldTokens > 0) {
            openHeldTokens--;
            accounting.released();
        }
    }

    private void closeAllTokens() {
        while (openHeldTokens > 0) {
            closeToken();
        }
    }

    private void pauseOnce() {
        if (!paused) {
            paused = true;
            ChannelReadPause.pause(ctx.channel());
        }
    }

    private void resumeOnce() {
        if (paused) {
            paused = false;
            ChannelReadPause.resume(ctx.channel());
        }
    }

    private boolean heldIsEmpty() {
        return held == null || held.isEmpty();
    }

    private static boolean isOutputShutdown(Channel channel) {
        return channel instanceof DuplexChannel && ((DuplexChannel) channel).isOutputShutdown();
    }

    private void armBackstop() {
        if (backstop == null) {
            progressAtLastTick = progress;
            stalledMillis = 0;
            if (backstopTask == null) {
                backstopTask = this::backstopTick;
            }
            backstop = ctx.executor().scheduleAtFixedRate(backstopTask, backstopTickMillis, backstopTickMillis, MILLISECONDS);
        }
    }

    private void cancelBackstop() {
        if (backstop != null) {
            backstop.cancel(false);
            backstop = null;
        }
    }

    private void backstopTick() {
        if (removed || heldIsEmpty()) {
            cancelBackstop();
            return;
        }
        // a terminal write still completing is a response being delivered, which the write-stall timeout bounds
        if (progress != progressAtLastTick || awaitingWrite) {
            progressAtLastTick = progress;
            stalledMillis = 0;
            return;
        }
        stalledMillis += backstopTickMillis;
        if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.DEBUG)
                    .setMessageFormat("{}requests held for{}ms on connection from:{}behind an exchange with no response progress")
                    .setArguments(heldRequests, stalledMillis, ctx.channel().remoteAddress())
            );
        }
        if (backstopMillis > 0) {
            if (stalledMillis >= backstopMillis) {
                cancelBackstop();
                wedgedCounter.run();
                if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setMessageFormat("closing connection from:{}as{}pipelined requests were held for{}ms behind an exchange with no response progress, which is inboundConnectionIdleTimeoutMillis")
                            .setArguments(ctx.channel().remoteAddress(), heldRequests, stalledMillis)
                    );
                }
                enterClosing();
                ctx.close();
            }
        } else if (stalledMillis >= DISABLED_BACKSTOP_REPORT_MILLIS && !wedgeReported) {
            wedgeReported = true;
            cancelBackstop();
            wedgedCounter.run();
            if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("{}pipelined requests held on connection from:{}behind an exchange with no response progress for{}ms; inbound connection idle timeout is disabled, so the connection is left open")
                        .setArguments(heldRequests, ctx.channel().remoteAddress(), stalledMillis)
                );
            }
        }
    }

    private void warnCapped() {
        if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("refusing pipelined requests from:{}past " + MAX_HELD_REQUESTS + " held behind an outstanding one; the held requests are answered, then the connection is closed")
                    .setArguments(ctx.channel().remoteAddress())
            );
        }
    }

    // package-private test views of the state machine, read on the event loop
    boolean outstanding() {
        return outstanding;
    }

    boolean awaitingWrite() {
        return awaitingWrite;
    }

    boolean bodyArriving() {
        return bodyArriving;
    }

    boolean closing() {
        return closing;
    }

    boolean drainScheduled() {
        return drainScheduled;
    }

    boolean droppedWhileClosing() {
        return droppedWhileClosing;
    }

    boolean releasing() {
        return releasing;
    }

    boolean paused() {
        return paused;
    }

    int heldRequests() {
        return heldRequests;
    }

    int openHeldTokens() {
        return openHeldTokens;
    }

    boolean headHeldFirst() {
        return !heldIsEmpty() && held.peekFirst() instanceof HttpRequest;
    }
}
