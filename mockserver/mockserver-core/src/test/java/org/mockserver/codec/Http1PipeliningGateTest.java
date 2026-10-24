package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;
import org.mockserver.responsewriter.ResponseWrittenBeneathCodecEvent;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.ConnectionClosing;
import org.mockserver.socket.ConnectionClosingEvent;
import org.mockserver.socket.LingeringClose;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * {@link Http1PipeliningGate} on an {@link EmbeddedChannel}: inbound objects are fired straight into the pipeline, so
 * the gate's deferred releases run only when a test runs the channel's pending tasks. Request bodies use pooled
 * buffers, tracked by the module's paranoid leak detector and checked released explicitly.
 */
public class Http1PipeliningGateTest {

    private static final long TICK = 100;

    private final Accounting accounting = new Accounting();
    private final CapturingLogger logger = new CapturingLogger();
    private final AtomicInteger wedged = new AtomicInteger();
    private final List<ByteBuf> tracked = new ArrayList<>();
    private TestChannel channel;
    private Http1PipeliningGate gate;
    private WriteHolder writes;
    private Downstream downstream;

    @After
    public void releaseEverything() {
        if (channel != null) {
            writes.failAll();
            channel.finishAndReleaseAll();
        }
        for (ByteBuf buffer : tracked) {
            assertThat("a request buffer was not released", buffer.refCnt(), is(0));
        }
    }

    // ---- U-1 ----

    @Test
    public void shouldHoldPipelinedRequestsAndReleaseExactlyOnePerDrain() {
        open(gate(1 << 20, 300_000));

        feed(head("/1"), LastHttpContent.EMPTY_LAST_CONTENT);
        feed(post("/2"), content("ab"), last("c"));
        feed(head("/3"), LastHttpContent.EMPTY_LAST_CONTENT);

        assertThat(downstream.events, contains("head /1", "last"));
        assertThat("a held complete request pauses reads", ChannelReadPause.holds(channel), is(1));
        assertThat(gate.heldRequests(), is(2));

        downstream.answer(ok());
        assertThat("released one task after the terminal write", downstream.events.size(), is(2));
        channel.runPendingTasks();
        assertThat(downstream.events, contains("head /1", "last", "head /2", "content", "last", "readComplete"));
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.runPendingTasks();
        assertThat("nothing more until /2 ends", downstream.heads(), contains("/1", "/2"));

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2", "/3"));
        assertThat("reads resume once nothing is held", ChannelReadPause.holds(channel), is(0));

        downstream.answer(ok());
        assertThat(Http1PipeliningGate.isSlowPath(channel), is(false));
        assertThat(accounting.open, is(0));
        assertThat(accounting.heldCalls, is(2));
    }

    @Test
    public void shouldHoldAndReleaseWithNoInFlightAccounting() {
        open(new Http1PipeliningGate(configuration(1 << 20, 300_000), null, logger, wedged::incrementAndGet));

        feed(head("/1"), LastHttpContent.EMPTY_LAST_CONTENT);
        feed(head("/2"), LastHttpContent.EMPTY_LAST_CONTENT);
        assertThat(gate.heldRequests(), is(1));

        downstream.answer(ok());
        channel.runPendingTasks();

        assertThat(downstream.heads(), contains("/1", "/2"));
    }

    @Test
    public void shouldPassTheAggregators413ForAnOversizedRequestThatIsNotPipelined() {
        Tail tail = new Tail();
        EmbeddedChannel codec = new EmbeddedChannel(new HttpServerCodec(), gate(1 << 20, 300_000), HttpObjectAggregators.httpObjectAggregator(16), tail);
        try {
            codec.writeInbound(Unpooled.copiedBuffer("GET /first HTTP/1.1\r\nHost: a\r\n\r\n", StandardCharsets.US_ASCII));
            codec.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER));
            readAll(codec);
            codec.runPendingTasks();

            codec.writeInbound(Unpooled.copiedBuffer("POST /oversized HTTP/1.1\r\nHost: a\r\nContent-Length: 100\r\n\r\n", StandardCharsets.US_ASCII));

            assertThat("written straight through the gate", readAll(codec), startsWith("HTTP/1.1 413 Request Entity Too Large"));
            assertThat(tail.uris(), contains("/first"));
        } finally {
            tail.releaseAll();
            codec.finishAndReleaseAll();
        }
    }

    private static String readAll(EmbeddedChannel codec) {
        StringBuilder written = new StringBuilder();
        for (Object outbound = codec.readOutbound(); outbound != null; outbound = codec.readOutbound()) {
            if (outbound instanceof ByteBuf) {
                written.append(((ByteBuf) outbound).toString(StandardCharsets.US_ASCII));
            }
            ReferenceCountUtil.release(outbound);
        }
        return written.toString();
    }

    // ---- U-2 ----

    @Test
    public void shouldEndExchangesOnlyOnFinalResponsesOrEndEvents() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(full("/2"));

        downstream.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        downstream.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(103)));
        downstream.write(LastHttpContent.EMPTY_LAST_CONTENT);
        channel.pipeline().fireUserEventTriggered(ResponseWrittenBeneathCodecEvent.INSTANCE);
        channel.runPendingTasks();
        assertThat("interim responses and the codec's beneath-write event end nothing", downstream.heads(), contains("/1"));

        // a final 1xx, which handlers after the codec cannot tell from an interim one, ends with the event
        channel.pipeline().fireUserEventTriggered(HttpExchangeEndedEvent.INSTANCE);
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));

        feed(full("/3"));
        channel.pipeline().fireUserEventTriggered(HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN);
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2", "/3"));

        feed(full("/4"));
        downstream.ctx.writeAndFlush(ok(), downstream.ctx.voidPromise());
        channel.runPendingTasks();
        assertThat("a void promise is unvoided so its completion is seen", downstream.heads(), contains("/1", "/2", "/3", "/4"));

        feed(full("/5"));
        downstream.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        downstream.write(new DefaultHttpContent(Unpooled.copiedBuffer("x", StandardCharsets.UTF_8)));
        channel.runPendingTasks();
        assertThat("a streamed response ends at its last content", downstream.heads(), contains("/1", "/2", "/3", "/4"));
        downstream.write(LastHttpContent.EMPTY_LAST_CONTENT);
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2", "/3", "/4", "/5"));
    }

    // ---- U-3 ----

    @Test
    public void shouldReleaseExactlyOneRequestForADuplicateEnd() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(full("/2"));
        feed(full("/3"));

        // the end event schedules a drain; the terminal write for the same exchange is still awaiting completion when
        // it runs, so the drain releases nothing
        channel.pipeline().fireUserEventTriggered(HttpExchangeEndedEvent.INSTANCE);
        writes.holding = true;
        downstream.write(ok());
        assertThat(gate.drainScheduled(), is(true));
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1"));

        writes.completeAll();
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));

        // a duplicate end while /2's write is awaiting completion releases nothing more than /3
        downstream.write(ok());
        channel.pipeline().fireUserEventTriggered(HttpExchangeEndedEvent.INSTANCE);
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));
        writes.completeAll();
        channel.runPendingTasks();
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2", "/3"));
    }

    // ---- U-4 ----

    @Test
    public void shouldCloseWithoutReleasingWhenATerminalWriteFails() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(full("/2"));

        writes.holding = true;
        downstream.write(ok());
        writes.failAll();
        channel.runPendingTasks();

        assertThat(channel.isOpen(), is(false));
        assertThat(downstream.heads(), contains("/1", "/2"));
        assertThat("the held request reaches the handlers only once the channel is inactive", downstream.activeAtHead.get(1), is(false));
        assertThat(accounting.open, is(0));
    }

    // ---- U-5 ----

    @Test
    public void shouldEnterClosingFromAConnectionCloseHead() {
        assertClosingWithoutRelease(() -> downstream.write(okClose()));
    }

    @Test
    public void shouldEnterClosingFromAMarkSetBeforeTheWrite() {
        assertClosingWithoutRelease(() -> {
            ConnectionClosing.mark(channel);
            downstream.write(ok());
        });
    }

    @Test
    public void shouldEnterClosingFromAlwaysCloseSocketConnectionsReadAtTheWrite() {
        Configuration configuration = configuration(1 << 20, 300_000);
        open(new Http1PipeliningGate(configuration, accounting, logger, wedged::incrementAndGet));
        assertClosingWithoutRelease(() -> {
            // changed after the gate was built, as PUT /configuration can
            configuration.alwaysCloseSocketConnections(true);
            downstream.write(ok());
        });
    }

    @Test
    public void shouldEnterClosingWhenTheOutputHasShutDown() {
        assertClosingWithoutRelease(() -> {
            channel.outputShutdown = true;
            downstream.write(ok());
        });
    }

    @Test
    public void shouldEnterClosingFromALingeringClose() {
        assertClosingWithoutRelease(() -> {
            downstream.write(ok());
            LingeringClose.close(channel);
        });
    }

    private void assertClosingWithoutRelease(Runnable trigger) {
        if (channel == null) {
            open(gate(1 << 20, 300_000));
        }
        feed(full("/1"));
        feed(full("/2"));
        assertThat(ChannelReadPause.holds(channel), is(1));

        trigger.run();
        channel.runPendingTasks();

        assertThat(gate.closing(), is(true));
        assertThat("no drain while closing", downstream.heads(), contains("/1"));
        assertThat("the gate's read pause is dropped at once", ChannelReadPause.holds(channel), is(0));

        channel.close();
        assertThat("held requests are fired at inactive", downstream.heads(), contains("/1", "/2"));
        assertThat(downstream.activeAtHead.get(1), is(false));
        assertThat(accounting.open, is(0));
    }

    // ---- U-6 ----

    @Test
    public void shouldChargeEachPinnedReadOnceAndPauseAtTheCostLimit() {
        int readCapacity = 64 * 1024;
        long limit = readCapacity + 100 * Http1PipeliningGate.OBJECT_COST;
        open(gate(limit, 300_000));
        ByteBuf read = trackedBuffer(readCapacity);
        read.writerIndex(readCapacity);

        feed(full("/1"));
        feed(post("/2"));
        long expected = Http1PipeliningGate.OBJECT_COST;
        int chunks = 0;
        while (Http1PipeliningGate.heldCost(channel) < limit) {
            assertThat("reads stay on below the limit", ChannelReadPause.holds(channel), is(0));
            feed(new DefaultHttpContent(read.retainedSlice(chunks, 1)));
            expected += Http1PipeliningGate.OBJECT_COST + (chunks == 0 ? readCapacity : 0);
            chunks++;
            assertThat("a shared read is charged once", Http1PipeliningGate.heldCost(channel), is(expected));
        }
        assertThat(ChannelReadPause.holds(channel), is(1));
        assertThat(chunks, is(99));

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat("released with its body still arriving", gate.bodyArriving(), is(true));
        assertThat(Http1PipeliningGate.heldCost(channel), is(0L));
        assertThat(ChannelReadPause.holds(channel), is(0));
        feed(new DefaultLastHttpContent(read.retainedSlice(chunks, 1)));
        assertThat(gate.bodyArriving(), is(false));

        // the read is pinned afresh by the next hold: the identity kept for charging was cleared when the hold emptied
        feed(post("/3"));
        feed(new DefaultHttpContent(read.retainedSlice(200, 1)));
        assertThat(Http1PipeliningGate.heldCost(channel), is(2 * Http1PipeliningGate.OBJECT_COST + readCapacity));
        read.release();
    }

    @Test
    public void shouldDropAndReleaseTheNewestIncompleteRequestPastTheLimitWhileClosing() {
        open(gate(4096, 300_000));
        feed(full("/1"));
        channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        feed(full("/2"));
        feed(post("/3"));
        feed(content(1000), content(1000));
        assertThat(gate.droppedWhileClosing(), is(false));
        feed(content(1000), content(1000));

        assertThat(gate.droppedWhileClosing(), is(true));
        assertThat("the complete request before it is kept", gate.heldRequests(), is(1));
        assertThat("no read pause is taken while closing", ChannelReadPause.holds(channel), is(0));
        feed(content(10), last("z"));
        feed(full("/4"));
        assertThat(gate.heldRequests(), is(1));
        assertThat(accounting.open, is(1));

        channel.close();
        assertThat(downstream.heads(), contains("/1", "/2"));
    }

    // ---- U-7 ----

    @Test
    public void shouldAnswer1025RequestsInOrderThenCloseWhenCapped() {
        assertCapped(300_000);
    }

    @Test
    public void shouldAnswer1025RequestsInOrderThenCloseWhenCappedWithTheIdleTimeoutDisabled() {
        assertCapped(0);
    }

    private void assertCapped(long idleTimeoutMillis) {
        open(gate(1 << 20, idleTimeoutMillis));
        int total = 1 + Http1PipeliningGate.MAX_HELD_REQUESTS + 6;
        for (int i = 1; i <= total; i++) {
            feed(full("/" + i));
        }
        assertThat(gate.heldRequests(), is(Http1PipeliningGate.MAX_HELD_REQUESTS));
        assertThat(Http1PipeliningGate.capHit(channel), is(true));
        assertThat(logger.count(Level.WARN, "refusing pipelined requests"), is(1L));

        int answered = 0;
        while (channel.isOpen() && answered < total) {
            assertThat("closed only after the last answer's write completes", channel.isOpen(), is(true));
            writes.holding = true;
            downstream.answer(ok());
            answered++;
            channel.runPendingTasks();
            assertThat(channel.isOpen(), is(true));
            writes.holding = false;
            writes.completeAll();
            channel.runPendingTasks();
        }
        assertThat(answered, is(Http1PipeliningGate.MAX_HELD_REQUESTS + 1));
        assertThat(channel.isOpen(), is(false));
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= Http1PipeliningGate.MAX_HELD_REQUESTS + 1; i++) {
            expected.add("/" + i);
        }
        assertThat(downstream.heads(), is(expected));
        assertThat(accounting.open, is(0));
    }

    // ---- U-8 ----

    @Test
    public void shouldStopTheReleaseWhenASwitchingProtocolsResponseRemovesTheGate() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(post("/upgrade"), content("frame bytes"), last("more"));
        feed(full("/3"));
        downstream.onRead = msg -> {
            if (msg instanceof HttpRequest && ((HttpRequest) msg).uri().equals("/upgrade")) {
                downstream.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));
            }
        };

        downstream.answer(ok());
        channel.runPendingTasks();

        assertThat(channel.pipeline().get(Http1PipeliningGate.class), nullValue());
        assertThat(downstream.events, contains("head /1", "head /upgrade"));
        assertThat(accounting.open, is(0));
        assertThat(ChannelReadPause.holds(channel), is(0));
        assertThat(logger.count(Level.WARN, "switched protocols"), is(1L));
    }

    @Test
    public void shouldLeaveAnotherHoldersPauseInPlace() {
        open(gate(1 << 20, 300_000));
        ChannelReadPause.pause(channel);
        feed(full("/1"));
        feed(full("/2"));
        assertThat(ChannelReadPause.holds(channel), is(2));

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));
        assertThat(ChannelReadPause.holds(channel), is(1));
        assertThat(channel.config().isAutoRead(), is(false));

        ChannelReadPause.resume(channel);
        assertThat(channel.config().isAutoRead(), is(true));
    }

    @Test
    public void shouldNeverReleaseAPauseItDidNotTake() {
        open(gate(1 << 20, 300_000));
        ChannelReadPause.pause(channel);
        feed(full("/1"));
        feed(post("/2"), content("incomplete"));
        assertThat("an incomplete held request below the limit takes no pause", ChannelReadPause.holds(channel), is(1));

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        assertThat(gate.closing(), is(true));
        assertThat(ChannelReadPause.holds(channel), is(1));
        ChannelReadPause.resume(channel);
    }

    // ---- U-9 (with the real aggregator) ----

    @Test
    public void shouldStripExpectFromACompleteRequestAndKeepItOnTheCurrentOne() {
        Tail tail = new Tail();
        ExpectationProbe probe = new ExpectationProbe();
        EmbeddedChannel codecChannel = codecChannel(probe, tail);

        codecChannel.writeInbound(ascii("GET /1 HTTP/1.1\r\nHost: x\r\n\r\n"
            + "POST /2 HTTP/1.1\r\nHost: x\r\nExpect: foo\r\nContent-Length: 5\r\n\r\nhello"
            + "POST /3 HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\n"));
        assertThat(tail.uris(), contains("/1"));

        tail.answer(ok());
        codecChannel.runPendingTasks();
        assertThat("the complete request is released without its expectation", tail.uris(), contains("/1", "/2"));
        assertThat(tail.requests.get(1).headers().contains(HttpHeaderNames.EXPECT), is(false));
        assertThat(tail.requests.get(1).content().toString(StandardCharsets.US_ASCII), is("hello"));
        assertThat("no 417 and no decoder reset", probe.events, is(0));

        tail.answer(ok());
        codecChannel.runPendingTasks();
        assertThat(outbound(codecChannel), containsString("HTTP/1.1 100 Continue"));
        codecChannel.writeInbound(ascii("world"));
        assertThat(tail.uris(), contains("/1", "/2", "/3"));
        assertThat(tail.requests.get(2).content().toString(StandardCharsets.US_ASCII), is("world"));
        assertThat(probe.events, is(0));
        tail.releaseAll();
        codecChannel.finishAndReleaseAll();
    }

    // ---- U-10 ----

    @Test
    public void shouldCountHeldRequestsUntilTheirLastContentHasBeenFired() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(post("/2"), content("a"));
        assertThat(accounting.heldCalls, is(1));
        assertThat(accounting.open, is(1));
        List<Integer> openWhenLastFired = new ArrayList<>();
        downstream.onRead = msg -> {
            if (msg instanceof LastHttpContent) {
                openWhenLastFired.add(accounting.open);
            }
        };

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat("released incomplete: still counted", accounting.open, is(1));
        feed(last("b"));
        assertThat("released only after the last content was fired", openWhenLastFired, contains(1));
        assertThat(accounting.open, is(0));
        assertThat(accounting.releasedCalls, is(1));

        feed(full("/3"));
        assertThat(accounting.open, is(1));
        List<Integer> openAtInactive = new ArrayList<>();
        downstream.onInactive = () -> openAtInactive.add(accounting.open);
        channel.close();
        assertThat("closed at inactive, before it is passed on", openAtInactive, contains(0));
    }

    @Test
    public void shouldCloseHeldTokensWhenRemoved() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(full("/2"));
        feed(post("/3"), content("partial"));
        assertThat(accounting.open, is(2));

        Http1PipeliningGate.removeFrom(channel.pipeline());

        assertThat(accounting.open, is(0));
        assertThat(downstream.heads(), contains("/1"));
    }

    // ---- U-11 ----

    @Test
    public void shouldCloseAConnectionWhoseHeldRequestsSeeNoProgressForTheIdleTimeout() {
        open(gate(1 << 20, 1_000));
        channel.freezeTime();
        feed(full("/1"));
        feed(full("/2"));

        advance(900);
        assertThat(channel.isOpen(), is(true));
        downstream.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        advance(900);
        assertThat("a response chunk is progress", channel.isOpen(), is(true));
        downstream.write(new DefaultHttpContent(Unpooled.copiedBuffer("x", StandardCharsets.UTF_8)));
        advance(900);
        assertThat(channel.isOpen(), is(true));
        assertThat(wedged.get(), is(0));

        advance(200);
        assertThat("no progress for the idle timeout", channel.isOpen(), is(false));
        assertThat(wedged.get(), is(1));
        assertThat(logger.count(Level.WARN, "no response progress"), is(1L));
        assertThat("held requests are fired at inactive", downstream.heads(), contains("/1", "/2"));
    }

    @Test
    public void shouldNotCountAResponseStillBeingWrittenAsNoProgress() {
        open(gate(1 << 20, 1_000));
        channel.freezeTime();
        feed(full("/1"));
        feed(full("/2"));
        // one large response, issued whole and paced to the socket beneath the gate
        writes.holding = true;
        downstream.write(ok());

        advance(5_000);
        assertThat("a response being delivered is progress", channel.isOpen(), is(true));
        assertThat(wedged.get(), is(0));

        writes.completeAll();
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1", "/2"));
        writes.holding = false;
        advance(1_000);
        assertThat("the held request was released, so nothing is timed", channel.isOpen(), is(true));
    }

    @Test
    public void shouldTickAtATenthOfTheIdleTimeoutWithinBounds() {
        assertThat(Http1PipeliningGate.backstopTickMillis(1_000), is(100L));
        assertThat(Http1PipeliningGate.backstopTickMillis(20_000), is(2_000L));
        assertThat(Http1PipeliningGate.backstopTickMillis(50), is(Http1PipeliningGate.MIN_BACKSTOP_TICK_MILLIS));
        assertThat(Http1PipeliningGate.backstopTickMillis(300_000), is(Http1PipeliningGate.MAX_BACKSTOP_TICK_MILLIS));
        assertThat("idle timeout disabled", Http1PipeliningGate.backstopTickMillis(0), is(Http1PipeliningGate.MAX_BACKSTOP_TICK_MILLIS));
    }

    @Test
    public void shouldStopTheBackstopWhenNothingIsHeld() {
        open(gate(1 << 20, 1_000));
        channel.freezeTime();
        feed(full("/1"));
        feed(full("/2"));
        downstream.answer(ok());
        channel.runPendingTasks();

        advance(5_000);
        assertThat("only the outstanding exchange is left, which the backstop does not time", channel.isOpen(), is(true));
        assertThat(wedged.get(), is(0));
    }

    // ---- U-12 ----

    @Test
    public void shouldFireHeldObjectsThenInactive() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(post("/2"), content("x"));

        channel.close();

        assertThat(downstream.events, contains("head /1", "head /2", "content", "readComplete", "inactive"));
    }

    @Test
    public void shouldReleaseNothingAfterAConnectAndDropTheHeldRequestsWhenRemoved() {
        open(gate(1 << 20, 300_000));
        feed(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "example.com:443"));
        feed(full("/tunnelled"));
        assertThat(ChannelReadPause.holds(channel), is(1));

        Http1PipeliningGate.switching(channel.pipeline());
        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("example.com:443"));

        Http1PipeliningGate.removeFrom(channel.pipeline());
        assertThat(channel.pipeline().get(Http1PipeliningGate.class), nullValue());
        assertThat(accounting.open, is(0));
        assertThat(ChannelReadPause.holds(channel), is(0));
        assertThat(downstream.heads(), contains("example.com:443"));
    }

    // ---- U-13 (real codec and aggregator) ----

    @Test
    public void shouldEndAReleasedRequestsBodyWhenTheAggregatorFailsItsExpectationInsideTheRelease() {
        assertExpectationFailureInsideRelease("Expect: 100-continue\r\nContent-Length: 1048576", "HTTP/1.1 413 Request Entity Too Large");
    }

    @Test
    public void shouldEndAReleasedRequestsBodyWhenTheAggregatorRejectsAnUnsupportedExpectationInsideTheRelease() {
        assertExpectationFailureInsideRelease("Expect: foo\r\nContent-Length: 10", "HTTP/1.1 417 Expectation Failed");
    }

    private void assertExpectationFailureInsideRelease(String headers, String rejection) {
        Tail tail = new Tail();
        ExpectationProbe probe = new ExpectationProbe();
        EmbeddedChannel codecChannel = codecChannel(probe, tail);

        // nothing after the POST yet: until the reset, the decoder reads any further bytes as its body
        codecChannel.writeInbound(ascii("GET /first HTTP/1.1\r\nHost: x\r\n\r\nPOST /upload HTTP/1.1\r\nHost: x\r\n" + headers + "\r\n\r\n"));
        assertThat(tail.uris(), contains("/first"));
        assertThat(accounting.open, is(1));

        tail.answer(ok());
        codecChannel.runPendingTasks();

        assertThat(outbound(codecChannel), containsString(rejection));
        assertThat("the event reached the gate while it was releasing the request", probe.duringRelease, contains(true));
        assertThat(accounting.open, is(0));
        assertThat(Http1PipeliningGate.isSlowPath(codecChannel), is(false));

        codecChannel.pipeline().fireChannelRead(ascii("GET /fast HTTP/1.1\r\nHost: x\r\n\r\n"));
        assertThat("answered from the read itself, on the hot path", tail.uris(), contains("/first", "/fast"));
        assertThat(Http1PipeliningGate.isSlowPath(codecChannel), is(false));
        tail.releaseAll();
        codecChannel.finishAndReleaseAll();
    }

    // ---- U-14 ----

    @Test
    public void shouldKeepTheReleaseInvariantUnderRandomInterleavings() {
        Coverage coverage = new Coverage();
        for (int run = 0; run < 400; run++) {
            new PropertyRun(new Random(run), coverage).run();
        }
        // the runs must reach the states the invariants are about, or they prove nothing
        assertThat("held", coverage.held, is(true));
        assertThat("closing", coverage.closing, is(true));
        assertThat("closing with requests held", coverage.closingWhileHolding, is(true));
        assertThat("a drop while closing", coverage.droppedWhileClosing, is(true));
        assertThat("a released body still arriving", coverage.bodyArriving, is(true));
        assertThat("an expectation failure inside a release", coverage.expectationFailedInsideRelease, is(true));
        assertThat("a fresh head fired while closing", coverage.freshHeadWhileClosing, is(true));
    }

    private static final class Coverage {
        private boolean held;
        private boolean closing;
        private boolean closingWhileHolding;
        private boolean droppedWhileClosing;
        private boolean bodyArriving;
        private boolean expectationFailedInsideRelease;
        private boolean freshHeadWhileClosing;
    }

    // ---- U-15 ----

    @Test
    public void shouldEnterClosingAndDropThePauseOnAnOutboundClose() {
        open(gate(1 << 20, 300_000));
        writes.swallowClose = true;
        feed(full("/1"));
        feed(full("/2"));
        assertThat(ChannelReadPause.holds(channel), is(1));

        downstream.ctx.close();

        assertThat(gate.closing(), is(true));
        assertThat(ChannelReadPause.holds(channel), is(0));
        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1"));
        writes.swallowClose = false;
    }

    @Test
    public void shouldEnterClosingOnTheEventLoopWhenMarkedFromAnotherThread() throws Exception {
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            Http1PipeliningGate localGate = gate(1 << 20, 300_000);
            Downstream localDownstream = new Downstream();
            LocalChannel local = new LocalChannel();
            local.pipeline().addLast(localGate, localDownstream);
            group.register(local).sync();
            local.eventLoop().submit(() -> {
                local.pipeline().fireChannelRead(full("/1"));
                local.pipeline().fireChannelRead(full("/2"));
            }).sync();
            assertThat(local.eventLoop().submit(() -> ChannelReadPause.holds(local)).get(), is(1));

            ConnectionClosing.mark(local);
            // tasks run in order, so the event fired by the mark has run once this one has
            local.eventLoop().submit(() -> {
            }).sync();

            assertThat(local.eventLoop().submit(localGate::closing).get(), is(true));
            assertThat(local.eventLoop().submit(() -> ChannelReadPause.holds(local)).get(), is(0));
            assertThat(localDownstream.heads(), contains("/1"));
            local.close().sync();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    // ---- U-16 ----

    @Test
    public void shouldReleaseNothingAndDropHeldRequestsOnceTheStopDrainHasEnded() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        feed(full("/2"));
        accounting.discard = true;

        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/1"));

        channel.close();
        assertThat("dropped, not processed", downstream.heads(), contains("/1"));
        assertThat(accounting.open, is(0));
    }

    // ---- U-17 ----

    @Test
    public void shouldNeverCloseWithTheIdleTimeoutDisabledButReportTheWedgeOnce() {
        open(gate(1 << 20, 0));
        channel.freezeTime();
        feed(full("/1"));
        feed(full("/2"));

        advance(Http1PipeliningGate.DISABLED_BACKSTOP_REPORT_MILLIS - TICK);
        assertThat(wedged.get(), is(0));
        advance(TICK);
        assertThat(wedged.get(), is(1));
        assertThat(logger.count(Level.WARN, "idle timeout is disabled"), is(1L));

        advance(Http1PipeliningGate.DISABLED_BACKSTOP_REPORT_MILLIS * 3);
        assertThat(channel.isOpen(), is(true));
        assertThat(wedged.get(), is(1));
        assertThat(logger.count(Level.WARN, "idle timeout is disabled"), is(1L));
    }

    // ---- U-18 ----

    @Test
    public void shouldFireAFreshHeadWhileClosingAndHoldAPipelinedOne() {
        open(gate(1 << 20, 300_000));
        channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        assertThat(gate.closing(), is(true));

        feed(full("/fresh"));
        assertThat(downstream.heads(), contains("/fresh"));
        assertThat(gate.outstanding(), is(true));
        assertThat("no token for a request that was never held", accounting.heldCalls, is(0));

        feed(full("/pipelined"));
        downstream.answer(ok());
        channel.runPendingTasks();
        assertThat(downstream.heads(), contains("/fresh"));

        feed(full("/after"));
        channel.close();
        assertThat(downstream.heads(), contains("/fresh", "/pipelined", "/after"));
        assertThat(downstream.activeAtHead.get(1), is(false));
    }

    @Test
    public void shouldFireAFreshHeadAfterTheLastWriteCompletesWhileClosing() {
        open(gate(1 << 20, 300_000));
        feed(full("/1"));
        // as a writer with a delayed close does before its response
        ConnectionClosing.mark(channel);
        downstream.answer(ok());
        assertThat(gate.closing(), is(true));
        feed(full("/fresh"));
        assertThat("not pipelined: answered as without the gate", downstream.heads(), contains("/1", "/fresh"));
        assertThat(accounting.heldCalls, is(0));
    }

    @Test
    public void shouldAcceptNothingAfterADropWhileClosing() {
        open(gate(4096, 300_000));
        feed(full("/1"));
        channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        feed(post("/big"), content(3000), content(3000));
        assertThat(gate.droppedWhileClosing(), is(true));
        assertThat(gate.heldRequests(), is(0));

        feed(content(10), last("rest"));
        downstream.answer(ok());
        channel.runPendingTasks();
        feed(full("/after"));

        assertThat("no later head counts as fresh", downstream.heads(), contains("/1"));
        assertThat(gate.droppedWhileClosing(), is(true));
        assertThat(gate.heldRequests(), is(0));
        channel.close();
        assertThat(downstream.heads(), contains("/1"));
    }

    @Test
    public void shouldNotFireAFreshHeadOnceTheOutputHasShutDown() {
        open(gate(1 << 20, 300_000));
        channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        channel.outputShutdown = true;

        feed(full("/late"));

        assertThat(downstream.heads(), empty());
        channel.close();
        assertThat(downstream.heads(), contains("/late"));
    }

    // ---- property run (U-14) ----

    private final class PropertyRun {
        private final Random random;
        private final Coverage coverage;
        private final List<Request> requests = new ArrayList<>();
        private int answeredUpTo;
        private boolean ending;

        PropertyRun(Random random, Coverage coverage) {
            this.random = random;
            this.coverage = coverage;
        }

        void run() {
            accounting.reset();
            tracked.clear();
            channel = null;
            long limit = new long[]{1 << 20, 4096, 1536}[random.nextInt(3)];
            open(gate(limit, 300_000));
            downstream.onRead = this::onDownstreamRead;
            boolean dropped = false;
            int downstreamObjectsAtDrop = 0;
            for (int step = 0; step < 120 && channel.isOpen(); step++) {
                int op = random.nextInt(100);
                if (op < 40) {
                    feedNext();
                } else if (op < 60) {
                    answer();
                } else if (op < 75) {
                    writes.completeOne(random.nextInt(20) != 0);
                } else if (op < 93) {
                    channel.runPendingTasks();
                } else if (op < 97) {
                    channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
                } else {
                    channel.pipeline().fireUserEventTriggered(HttpExchangeEndedEvent.INSTANCE);
                    answerOldestByEvent();
                }
                if (!channel.isOpen()) {
                    break;
                }
                if (dropped) {
                    assertThat("nothing after a drop while closing is fired", downstream.inboundObjects, is(downstreamObjectsAtDrop));
                } else if (gate.droppedWhileClosing()) {
                    dropped = true;
                    downstreamObjectsAtDrop = downstream.inboundObjects;
                }
                assertInvariants();
                coverage.held |= gate.heldRequests() > 0;
                coverage.closing |= gate.closing();
                coverage.closingWhileHolding |= gate.closing() && gate.heldRequests() > 0;
                coverage.droppedWhileClosing |= gate.droppedWhileClosing();
                coverage.bodyArriving |= gate.bodyArriving();
            }
            // held requests are fired at inactive, whatever is unanswered
            ending = true;
            writes.failAll();
            channel.finishAndReleaseAll();
            for (Request request : requests) {
                request.dropRest();
            }
            for (ByteBuf buffer : tracked) {
                assertThat("a request buffer was not released", buffer.refCnt(), is(0));
            }
            tracked.clear();
            channel = null;
        }

        private void assertInvariants() {
            if (gate.headHeldFirst() && !gate.outstanding() && !gate.awaitingWrite() && !gate.closing()) {
                assertThat("a held request with nothing outstanding must have a release scheduled", gate.drainScheduled(), is(true));
            }
            int expectedTokens = gate.heldRequests() + (gate.bodyArriving() ? 1 : 0);
            assertThat(gate.openHeldTokens(), is(expectedTokens));
            assertThat(accounting.open, is(expectedTokens));
        }

        private void feedNext() {
            Request current = requests.isEmpty() ? null : requests.get(requests.size() - 1);
            if (current == null || current.fullyFed()) {
                if (requests.size() >= 10) {
                    return;
                }
                current = new Request(requests.size(), random);
                requests.add(current);
                boolean fresh = gate.heldRequests() == 0 && !gate.outstanding() && !gate.awaitingWrite()
                    && !gate.droppedWhileClosing() && !Http1PipeliningGate.capHit(channel);
                int before = downstream.heads().size();
                feed(current.next());
                if (fresh) {
                    assertThat("a head with nothing held or outstanding is fired at once", downstream.heads().size(), is(before + 1));
                    coverage.freshHeadWhileClosing |= gate.closing();
                }
                return;
            }
            feed(current.next());
        }

        private void onDownstreamRead(Object msg) {
            if (msg instanceof HttpRequest) {
                Request request = requests.get(Integer.parseInt(((HttpRequest) msg).uri().substring(1)));
                for (int i = 0; i < request.id && !ending && channel.isActive(); i++) {
                    assertThat("request " + request.id + " reached the handlers before request " + i + " was answered", requests.get(i).answered, is(true));
                }
                request.received = true;
                if (((HttpRequest) msg).headers().contains(HttpHeaderNames.EXPECT)) {
                    // as the aggregator does: the event, then a keep-alive 417, both inside the head's fire
                    coverage.expectationFailedInsideRelease |= gate.releasing() && gate.bodyArriving();
                    channel.pipeline().fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
                    request.answered = true;
                    writes.holding = random.nextBoolean();
                    downstream.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED));
                    if (random.nextBoolean()) {
                        // the decoder reset drops the rest of the body
                        request.dropRest();
                    }
                }
            }
            if (msg instanceof LastHttpContent) {
                for (int i = requests.size() - 1; i >= 0; i--) {
                    if (requests.get(i).received) {
                        requests.get(i).complete = true;
                        break;
                    }
                }
            }
        }

        private void answer() {
            for (int i = answeredUpTo; i < requests.size(); i++) {
                Request request = requests.get(i);
                if (request.answered) {
                    answeredUpTo = i + 1;
                    continue;
                }
                if (!request.received || (!request.complete && random.nextInt(4) != 0)) {
                    return;
                }
                request.answered = true;
                writes.holding = random.nextBoolean();
                int kind = random.nextInt(20);
                if (kind == 0) {
                    downstream.write(okClose());
                } else if (kind < 5) {
                    downstream.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
                    downstream.write(LastHttpContent.EMPTY_LAST_CONTENT);
                } else {
                    downstream.write(ok());
                }
                return;
            }
        }

        private void answerOldestByEvent() {
            for (Request request : requests) {
                if (!request.answered && request.received) {
                    request.answered = true;
                    return;
                }
                if (!request.answered) {
                    return;
                }
            }
        }

        private final class Request {
            private final int id;
            private final List<Object> objects = new ArrayList<>();
            private int fed;
            private boolean received;
            private boolean complete;
            private boolean answered;

            Request(int id, Random random) {
                this.id = id;
                int kind = random.nextInt(4);
                if (kind == 0) {
                    objects.add(full("/" + id));
                } else {
                    HttpRequest head = post("/" + id);
                    if (kind == 1) {
                        head.headers().set(HttpHeaderNames.EXPECT, "foo");
                    }
                    objects.add(head);
                    int contents = random.nextInt(4);
                    for (int i = 0; i < contents; i++) {
                        objects.add(content(random.nextInt(900) + 1));
                    }
                    objects.add(last("z"));
                }
            }

            Object next() {
                return objects.get(fed++);
            }

            boolean fullyFed() {
                return fed == objects.size();
            }

            void dropRest() {
                while (fed < objects.size()) {
                    ReferenceCountUtil.release(objects.get(fed++));
                }
            }
        }
    }

    // ---- harness ----

    private void open(Http1PipeliningGate gate) {
        this.gate = gate;
        channel = new TestChannel();
        writes = new WriteHolder();
        downstream = new Downstream();
        channel.pipeline().addLast(writes, gate, downstream);
    }

    private Http1PipeliningGate gate(long maxRequestBodySize, long idleTimeoutMillis) {
        return new Http1PipeliningGate(configuration(maxRequestBodySize, idleTimeoutMillis), accounting, logger, wedged::incrementAndGet);
    }

    private static Configuration configuration(long maxRequestBodySize, long idleTimeoutMillis) {
        return Configuration.configuration()
            .maxRequestBodySize((int) maxRequestBodySize)
            .inboundConnectionIdleTimeoutMillis(idleTimeoutMillis)
            .alwaysCloseSocketConnections(false);
    }

    private EmbeddedChannel codecChannel(ExpectationProbe probe, Tail tail) {
        gate = gate(1 << 20, 300_000);
        probe.gate = gate;
        return new EmbeddedChannel(new HttpServerCodec(), gate, probe, HttpObjectAggregators.httpObjectAggregator(1024), tail);
    }

    private void feed(Object... objects) {
        for (Object object : objects) {
            channel.pipeline().fireChannelRead(object);
        }
    }

    private void advance(long millis) {
        for (long elapsed = 0; elapsed < millis; elapsed += TICK) {
            channel.advanceTimeBy(TICK, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
        }
        channel.runPendingTasks();
    }

    private static HttpRequest head(String uri) {
        return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    private static HttpRequest post(String uri) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        return request;
    }

    private FullHttpRequest full(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, trackedBuffer(0));
    }

    private HttpContent content(String text) {
        ByteBuf buffer = trackedBuffer(text.length());
        buffer.writeCharSequence(text, StandardCharsets.US_ASCII);
        return new DefaultHttpContent(buffer);
    }

    private HttpContent content(int bytes) {
        ByteBuf buffer = trackedBuffer(bytes);
        buffer.writerIndex(bytes);
        return new DefaultHttpContent(buffer);
    }

    private LastHttpContent last(String text) {
        ByteBuf buffer = trackedBuffer(text.length());
        buffer.writeCharSequence(text, StandardCharsets.US_ASCII);
        return new DefaultLastHttpContent(buffer);
    }

    private ByteBuf trackedBuffer(int capacity) {
        // never 0: an empty allocation is the shared, unreleasable empty buffer
        int size = Math.max(capacity, 1);
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.directBuffer(size, size);
        tracked.add(buffer);
        return buffer;
    }

    private static ByteBuf ascii(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII);
    }

    private static FullHttpResponse ok() {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        return response;
    }

    private static FullHttpResponse okClose() {
        FullHttpResponse response = ok();
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        return response;
    }

    private static String outbound(EmbeddedChannel channel) {
        StringBuilder written = new StringBuilder();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ByteBuf) {
                written.append(((ByteBuf) message).toString(StandardCharsets.US_ASCII));
            }
            ReferenceCountUtil.release(message);
        }
        return written.toString();
    }

    private static final class Accounting implements HeldRequestAccounting {
        private int open;
        private int heldCalls;
        private int releasedCalls;
        private boolean discard;

        @Override
        public void held() {
            open++;
            heldCalls++;
        }

        @Override
        public void released() {
            open--;
            releasedCalls++;
        }

        @Override
        public boolean discardHeldOnClose() {
            return discard;
        }

        void reset() {
            open = 0;
            heldCalls = 0;
            releasedCalls = 0;
            discard = false;
        }
    }

    private static final class CapturingLogger extends MockServerLogger {
        private final List<LogEntry> entries = new ArrayList<>();

        CapturingLogger() {
            super(Http1PipeliningGateTest.class);
        }

        @Override
        public boolean isEnabledForInstance(Level level) {
            return true;
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            entries.add(logEntry);
        }

        long count(Level level, String fragment) {
            return entries.stream().filter(entry -> entry.getLogLevel() == level && entry.getMessageFormat().contains(fragment)).count();
        }
    }

    /**
     * A socket-like embedded channel, so the gate can see its output shut down and a lingering close can end it.
     */
    private static final class TestChannel extends EmbeddedChannel implements DuplexChannel {
        private boolean outputShutdown;

        @Override
        public boolean isInputShutdown() {
            return false;
        }

        @Override
        public ChannelFuture shutdownInput() {
            return newSucceededFuture();
        }

        @Override
        public ChannelFuture shutdownInput(ChannelPromise promise) {
            return promise.setSuccess();
        }

        @Override
        public boolean isOutputShutdown() {
            return outputShutdown;
        }

        @Override
        public ChannelFuture shutdownOutput() {
            outputShutdown = true;
            return newSucceededFuture();
        }

        @Override
        public ChannelFuture shutdownOutput(ChannelPromise promise) {
            outputShutdown = true;
            return promise.setSuccess();
        }

        @Override
        public boolean isShutdown() {
            return outputShutdown;
        }

        @Override
        public ChannelFuture shutdown() {
            outputShutdown = true;
            return newSucceededFuture();
        }

        @Override
        public ChannelFuture shutdown(ChannelPromise promise) {
            outputShutdown = true;
            return promise.setSuccess();
        }
    }

    /**
     * Sits beneath the gate in place of the socket: completes each write at once, or while holding keeps it and its
     * promise for the test to complete or fail. Writes never reach the embedded channel's own flush, which would run
     * the gate's pending releases there and then.
     */
    private static final class WriteHolder extends ChannelOutboundHandlerAdapter {
        private final List<Object> messages = new ArrayList<>();
        private final List<ChannelPromise> promises = new ArrayList<>();
        private final List<Object> written = new ArrayList<>();
        private boolean holding;
        private boolean swallowClose;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (holding) {
                messages.add(msg);
                promises.add(promise);
            } else {
                record(msg);
                promise.trySuccess();
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // nothing reaches the channel
        }

        private void record(Object msg) {
            written.add(msg instanceof HttpResponse ? ((HttpResponse) msg).status().code() : msg.getClass().getSimpleName());
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            if (swallowClose) {
                promise.setSuccess();
            } else {
                ctx.close(promise);
            }
        }

        void completeAll() {
            while (!promises.isEmpty()) {
                completeOne(true);
            }
        }

        void failAll() {
            while (!promises.isEmpty()) {
                completeOne(false);
            }
        }

        void completeOne(boolean success) {
            if (promises.isEmpty()) {
                return;
            }
            record(messages.remove(0));
            ChannelPromise promise = promises.remove(0);
            if (success) {
                promise.trySuccess();
            } else {
                promise.tryFailure(new java.io.IOException("write failed"));
            }
        }
    }

    private static final class Downstream extends ChannelInboundHandlerAdapter {
        private final List<String> events = new ArrayList<>();
        private final List<Boolean> activeAtHead = new ArrayList<>();
        private ChannelHandlerContext ctx;
        private Consumer<Object> onRead;
        private Runnable onInactive;
        private int inboundObjects;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            inboundObjects++;
            if (msg instanceof HttpRequest) {
                events.add("head " + ((HttpRequest) msg).uri());
                activeAtHead.add(ctx.channel().isActive());
            } else if (msg instanceof LastHttpContent) {
                events.add("last");
            } else {
                events.add("content");
            }
            try {
                if (onRead != null) {
                    onRead.accept(msg);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            events.add("readComplete");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            events.add("inactive");
            if (onInactive != null) {
                onInactive.run();
            }
        }

        List<String> heads() {
            List<String> heads = new ArrayList<>();
            for (String event : events) {
                if (event.startsWith("head ")) {
                    heads.add(event.substring(5));
                }
            }
            return heads;
        }

        void write(Object response) {
            ctx.writeAndFlush(response);
        }

        void answer(Object response) {
            write(response);
        }
    }

    private static final class Tail extends ChannelInboundHandlerAdapter {
        private final List<FullHttpRequest> requests = new ArrayList<>();
        private ChannelHandlerContext ctx;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            requests.add((FullHttpRequest) msg);
        }

        List<String> uris() {
            List<String> uris = new ArrayList<>();
            for (FullHttpRequest request : requests) {
                uris.add(request.uri());
            }
            return uris;
        }

        void answer(Object response) {
            ctx.writeAndFlush(response);
        }

        void releaseAll() {
            requests.forEach(ReferenceCountUtil::release);
        }
    }

    /**
     * Between the gate and the aggregator: records whether an expectation failure reached the gate inside its release.
     */
    private static final class ExpectationProbe extends ChannelInboundHandlerAdapter {
        private final List<Boolean> duringRelease = new ArrayList<>();
        private Http1PipeliningGate gate;
        private int events;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof HttpExpectationFailedEvent) {
                events++;
                duringRelease.add(gate.releasing());
            }
            ctx.fireUserEventTriggered(evt);
        }
    }

}
