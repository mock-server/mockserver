package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.BinaryResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ChannelReadPause;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.Delay.uniform;

/**
 * A binary expectation's response delay holds its reply back for that long, counted from the message's arrival, and
 * every later reply MockServer writes on the same connection waits behind it, so replies keep the order of the
 * messages they answer.
 */
public class BinaryRequestProxyingHandlerDelayTest {

    private final MockServerLogger logger = mock(MockServerLogger.class);
    private final HttpState httpState = mock(HttpState.class);
    private final Map<String, BinaryResponse> expectations = new LinkedHashMap<>();
    private final List<String> matched = new ArrayList<>();
    private EmbeddedChannel channel;

    @Before
    public void connection() {
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenAnswer(invocation -> {
            String received = new String(invocation.<BinaryRequestDefinition>getArgument(0).getBinaryData(), StandardCharsets.UTF_8);
            matched.add(received);
            BinaryResponse response = expectations.get(received);
            return response != null ? new Expectation(binaryRequest(received.getBytes(StandardCharsets.UTF_8))).thenRespondWithBinary(response) : null;
        });
        channel = new EmbeddedChannel();
        channel.freezeTime();
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration(), logger, mock(Scheduler.class), mock(NettyHttpClient.class), httpState));
    }

    @After
    public void releaseChannel() {
        channel.finishAndReleaseAll();
    }

    private void expect(String request, String reply, long delayMillis) {
        BinaryResponse response = binaryResponse(reply.getBytes(StandardCharsets.UTF_8));
        if (delayMillis > 0) {
            response.withDelay(MILLISECONDS, delayMillis);
        }
        expectations.put(request, response);
    }

    private void send(String message) {
        channel.writeInbound(Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        channel.checkException();
    }

    private void after(long millis) {
        channel.advanceTimeBy(millis, MILLISECONDS);
        channel.runScheduledPendingTasks();
        channel.checkException();
    }

    /** As a client stops or starts reading what MockServer writes to it, its connection stops or starts being writable. */
    private void clientWritable(boolean writable) {
        channel.unsafe().outboundBuffer().setUserDefinedWritability(1, writable);
        channel.runPendingTasks();
        channel.checkException();
    }

    private boolean isReading() {
        return channel.config().isAutoRead();
    }

    private List<String> written() {
        List<String> written = new ArrayList<>();
        ByteBuf buffer;
        while ((buffer = channel.readOutbound()) != null) {
            try {
                written.add(buffer.toString(StandardCharsets.UTF_8));
            } finally {
                buffer.release();
            }
        }
        return written;
    }

    @Test
    public void shouldWriteADelayedReplyOnlyOnceItsDelayHasPassed() {
        expect("slow", "SLOW", 500);

        send("slow");
        assertThat(written(), is(empty()));

        after(499);
        assertThat("not before the delay", written(), is(empty()));

        after(1);
        assertThat(written(), contains("SLOW"));
        assertThat(channel.isOpen(), is(true));
    }

    @Test
    public void shouldWriteAReplyWithoutADelayAtOnce() {
        expect("fast", "FAST", 0);

        send("fast");

        assertThat(written(), contains("FAST"));
    }

    @Test
    public void shouldHoldAReplyWithoutADelayBehindAnEarlierDelayedOne() {
        expect("slow", "SLOW", 500);
        expect("fast", "FAST", 0);

        send("slow");
        send("fast");
        assertThat("the later reply waits for the earlier one", written(), is(empty()));

        after(500);
        assertThat(written(), contains("SLOW", "FAST"));

        send("fast");
        assertThat("once nothing waits, a reply is written at once", written(), contains("FAST"));
    }

    @Test
    public void shouldHoldAShorterDelayBehindALongerEarlierOne() {
        expect("slow", "SLOW", 500);
        expect("quick", "QUICK", 100);

        send("slow");
        send("quick");

        after(100);
        assertThat("its own delay has passed, but the earlier reply's has not", written(), is(empty()));

        after(400);
        assertThat(written(), contains("SLOW", "QUICK"));
    }

    @Test
    public void shouldCountEachDelayFromItsOwnMessage() {
        expect("slow", "SLOW", 500);
        expect("slower", "SLOWER", 600);

        send("slow");
        send("slower");

        after(500);
        assertThat(written(), contains("SLOW"));

        after(100);
        assertThat("600 ms after its message, not 600 ms after the reply before it", written(), contains("SLOWER"));
    }

    @Test
    public void shouldWriteNoReplyAndCancelItsTimerOnceTheConnectionCloses() {
        expect("slow", "SLOW", 500);

        send("slow");
        // what a closed connection's pipeline sees; EmbeddedChannel.close() would also cancel every scheduled task itself
        channel.pipeline().fireChannelInactive();

        assertThat("the timer is cancelled", channel.runScheduledPendingTasks(), is(-1L));
        after(500);
        assertThat(written(), is(empty()));
    }

    @Test
    public void shouldCloseOnlyAfterTheDelayedReplyBeforeAnUnmatchedMessage() {
        expect("slow", "SLOW", 500);
        expect("fast", "FAST", 0);

        send("slow");
        send("unknown");
        send("fast");
        assertThat(written(), is(empty()));
        assertThat("the connection waits for the delayed reply", channel.isOpen(), is(true));

        after(500);
        List<String> written = written();
        assertThat(written.size(), is(2));
        assertThat(written.get(0), is("SLOW"));
        assertThat(written.get(1), startsWith("unknown message format"));
        assertThat(channel.isOpen(), is(false));
        assertThat("what came after the unmatched message is not matched, so uses up no expectation", matched, contains("slow", "unknown"));
    }

    @Test
    public void shouldStopReadingWhileTooManyRepliesWaitAndReadAgainOnceTheyAreWritten() {
        expect("slow", "SLOW", 500);
        expect("fast", "FAST", 0);

        send("slow");
        for (int i = 0; i < BinaryLocalReplies.MAX_WAITING_REPLIES - 1; i++) {
            send("fast");
        }
        assertThat("at the limit, still read", ChannelReadPause.holds(channel), is(0));

        send("fast");
        assertThat("past the limit, not read", ChannelReadPause.holds(channel), is(1));

        after(500);
        assertThat(written().size(), is(BinaryLocalReplies.MAX_WAITING_REPLIES + 1));
        assertThat("read again once every reply is written", ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldNotReadAClientThatCannotBeWrittenToOnceItsReplyIsWritten() {
        expect("fast", "FAST", 0);
        clientWritable(false);

        send("fast");
        assertThat("the reply is still written", written(), contains("FAST"));
        assertThat("a client that does not read its replies is not read either", isReading(), is(false));
        assertThat(ChannelReadPause.holds(channel), is(1));

        send("fast");
        assertThat(written(), contains("FAST"));
        assertThat("one hold, however many replies", ChannelReadPause.holds(channel), is(1));

        clientWritable(true);
        assertThat("read again once it can be written to", isReading(), is(true));
        assertThat(ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldNotReadAClientWithADelayedReplyWaitingWhileItCannotBeWrittenTo() {
        expect("slow", "SLOW", 500);

        send("slow");
        clientWritable(false);
        assertThat(isReading(), is(false));
        after(500);
        assertThat(written(), contains("SLOW"));
        assertThat(isReading(), is(false));

        clientWritable(true);
        assertThat(isReading(), is(true));
        assertThat(ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldKeepNotReadingAClientThatCanBeWrittenToAgainWhileTooManyRepliesStillWait() {
        expect("slow", "SLOW", 500);
        expect("fast", "FAST", 0);
        send("slow");
        for (int i = 0; i < BinaryLocalReplies.MAX_WAITING_REPLIES; i++) {
            send("fast");
        }
        clientWritable(false);
        clientWritable(true);
        assertThat("the replies still wait", isReading(), is(false));
        assertThat("one hold for both reasons", ChannelReadPause.holds(channel), is(1));

        after(500);
        assertThat(written().size(), is(BinaryLocalReplies.MAX_WAITING_REPLIES + 1));
        assertThat(isReading(), is(true));
    }

    @Test
    public void shouldKeepNotReadingAClientThatCannotBeWrittenToOnceItsWaitingRepliesAreWritten() {
        expect("slow", "SLOW", 500);
        expect("fast", "FAST", 0);
        send("slow");
        for (int i = 0; i < BinaryLocalReplies.MAX_WAITING_REPLIES; i++) {
            send("fast");
        }
        clientWritable(false);

        after(500);
        assertThat(written().size(), is(BinaryLocalReplies.MAX_WAITING_REPLIES + 1));
        assertThat("every reply is written, and none read", isReading(), is(false));
        assertThat(ChannelReadPause.holds(channel), is(1));

        clientWritable(true);
        assertThat(isReading(), is(true));
    }

    @Test
    public void shouldGiveUpTheHoldOnAClientThatCannotBeWrittenToWhenItCloses() {
        expect("fast", "FAST", 0);
        clientWritable(false);
        send("fast");
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.pipeline().fireChannelInactive();

        assertThat(ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldNotHoldAConnectionClosedByItsReply() {
        clientWritable(false);

        send("unknown");

        assertThat(written().get(0), startsWith("unknown message format"));
        assertThat(channel.isOpen(), is(false));
        assertThat(ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldKeepReadingAClientThatReadsItsReplies() {
        expect("fast", "FAST", 0);

        for (int i = 0; i < 10; i++) {
            send("fast");
        }

        assertThat(written().size(), is(10));
        assertThat(isReading(), is(true));
        assertThat(ChannelReadPause.holds(channel), is(0));
    }

    @Test
    public void shouldApplyADelayDrawnFromADistribution() {
        expectations.put("uniform", binaryResponse("UNIFORM".getBytes(StandardCharsets.UTF_8)).withDelay(uniform(MILLISECONDS, 200, 300)));

        send("uniform");
        after(199);
        assertThat(written(), is(empty()));

        after(101);
        assertThat(written(), contains("UNIFORM"));
    }
}
