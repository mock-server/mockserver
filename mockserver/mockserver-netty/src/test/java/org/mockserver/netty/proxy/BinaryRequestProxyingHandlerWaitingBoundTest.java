package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketCommunicationException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryMessage;
import org.mockserver.model.BinaryProxyListener;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ChannelReadPause;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_UNANSWERED_FORWARDS;

/**
 * Forwarding binary messages one per upstream connection and waiting for each response, what one client connection
 * keeps for messages already forwarded is bounded: the client is not read while
 * {@link BinaryRequestProxyingHandler#MAX_UNANSWERED_FORWARDS} of its forwards are not yet answered and handled, and a
 * forward with no response within maxSocketTimeoutInMillis fails, closing the client connection.
 */
public class BinaryRequestProxyingHandlerWaitingBoundTest {

    private static final long MAX_SOCKET_TIMEOUT_MILLIS = 5_000;

    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    private final MockServerLogger logger = mock(MockServerLogger.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final List<CompletableFuture<BinaryMessage>> upstreamResponses = new ArrayList<>();
    /** Each response's handling, which the scheduler runs once the response has completed. */
    private final Map<CompletableFuture<BinaryMessage>, Runnable> handling = new LinkedHashMap<>();
    private final List<Integer> reportedToListener = new ArrayList<>();
    private EmbeddedChannel channel;
    private int nextMessage;

    @After
    public void releaseChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    /** An upstream that accepts every message at once and answers only when the test says. */
    private void upstreamThatAnswersWhenTold() {
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any()))
            .thenAnswer(invocation -> {
                CompletableFuture<BinaryMessage> response = new CompletableFuture<>();
                upstreamResponses.add(response);
                return response;
            });
        doAnswer(invocation -> handling.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(scheduler).submit(any(CompletableFuture.class), any(Runnable.class), eq(false));
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
    }

    private void clientConnection(Configuration configuration) {
        channel = new EmbeddedChannel();
        // scheduled tasks then run only as the test advances time
        channel.freezeTime();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration, logger, scheduler, httpClient, mock(HttpState.class)));
    }

    private static Configuration waitingForResponses() {
        return configuration()
            .forwardBinaryRequestsUseSingleConnection(false)
            .forwardBinaryRequestsWithoutWaitingForResponse(false)
            .maxSocketTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS)
            .maxFutureTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS * 10);
    }

    private void clientSends(int messages) {
        for (int i = 0; i < messages; i++) {
            ByteBuf message = Unpooled.buffer(8).writeInt(nextMessage++).writeInt(0);
            channel.writeInbound(message);
        }
    }

    private static BinaryMessage reply(int sequenceNumber) {
        return BinaryMessage.bytes(Unpooled.buffer(4).writeInt(sequenceNumber).array());
    }

    private boolean isReading() {
        return channel.config().isAutoRead() && ChannelReadPause.holds(channel) == 0;
    }

    /** The upstream answers the message. */
    private void upstreamAnswers(int sequenceNumber) {
        upstreamResponses.get(sequenceNumber).complete(reply(sequenceNumber));
    }

    /** Runs, as the scheduler would, the handling of every response that has completed. */
    private void responsesHandled() {
        Iterator<Map.Entry<CompletableFuture<BinaryMessage>, Runnable>> waiting = handling.entrySet().iterator();
        while (waiting.hasNext()) {
            Map.Entry<CompletableFuture<BinaryMessage>, Runnable> next = waiting.next();
            if (next.getKey().isDone()) {
                waiting.remove();
                next.getValue().run();
            }
        }
        channel.runPendingTasks();
    }

    private void advanceTimeBy(long millis) {
        channel.advanceTimeBy(millis, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
    }

    private List<Integer> repliesWrittenToTheClient() {
        List<Integer> replies = new ArrayList<>();
        ByteBuf written;
        while ((written = channel.readOutbound()) != null) {
            replies.add(written.readInt());
            written.release();
        }
        return replies;
    }

    private List<LogEntry> failedForwardEntries() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN)
            .collect(Collectors.toList());
    }

    private static Throwable failureOf(CompletableFuture<BinaryMessage> response) {
        try {
            response.getNow(null);
        } catch (CompletionException failed) {
            return failed.getCause();
        }
        throw new AssertionError("completed without failing");
    }

    private static List<Integer> sequence(int from, int to) {
        List<Integer> sequence = new ArrayList<>();
        for (int i = from; i < to; i++) {
            sequence.add(i);
        }
        return sequence;
    }

    @Test
    public void shouldStopReadingTheClientOnceTheLimitOfForwardsWaitForAResponse() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());

        clientSends(MAX_UNANSWERED_FORWARDS - 1);
        assertThat("below the limit the client is still read", isReading(), is(true));

        clientSends(1);
        assertThat("at it, it is not", isReading(), is(false));
        assertThat("each message is forwarded at once all the same", upstreamResponses, hasSize(MAX_UNANSWERED_FORWARDS));

        // what a read already under way still delivers
        clientSends(2);
        assertThat("the hold is taken once, however many more arrive", ChannelReadPause.holds(channel), is(1));
        assertThat("nothing is closed for it", channel.isOpen(), is(true));
    }

    @Test
    public void shouldReadTheClientAgainOnceHalfAreAnsweredWritingEveryResponse() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(MAX_UNANSWERED_FORWARDS);
        assertThat(isReading(), is(false));

        int answered = 0;
        while (answered < MAX_UNANSWERED_FORWARDS / 2 - 1) {
            upstreamAnswers(answered++);
            responsesHandled();
            assertThat("still not read with " + (MAX_UNANSWERED_FORWARDS - answered) + " waiting", isReading(), is(false));
        }
        upstreamAnswers(answered++);
        responsesHandled();
        assertThat("read again with " + MAX_UNANSWERED_FORWARDS / 2 + " waiting", isReading(), is(true));

        clientSends(3);
        while (answered < nextMessage) {
            upstreamAnswers(answered++);
        }
        responsesHandled();
        assertThat("every response is written to the client", repliesWrittenToTheClient(), is(sequence(0, nextMessage)));
        assertThat(isReading(), is(true));
        assertThat(failedForwardEntries(), is(empty()));
    }

    @Test
    public void shouldCountAForwardUntilItsResponseHasBeenHandled() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(MAX_UNANSWERED_FORWARDS);

        for (int i = 0; i < MAX_UNANSWERED_FORWARDS; i++) {
            upstreamAnswers(i);
        }
        channel.runPendingTasks();
        assertThat("answered, but not yet written to the client, or reported to a listener", isReading(), is(false));

        responsesHandled();
        assertThat(isReading(), is(true));
    }

    @Test
    public void shouldHoldBackTheClientWhileAListenerIsBehind() {
        upstreamThatAnswersWhenTold();
        BinaryProxyListener listener = (binaryRequest, binaryResponse, serverAddress, clientAddress) -> reportedToListener.add(Unpooled.wrappedBuffer(binaryRequest.getBytes()).readInt());
        clientConnection(waitingForResponses().binaryProxyListener(listener));
        clientSends(MAX_UNANSWERED_FORWARDS);
        for (int i = 0; i < MAX_UNANSWERED_FORWARDS; i++) {
            upstreamAnswers(i);
        }

        // the listener is called as each response is handled
        List<Map.Entry<CompletableFuture<BinaryMessage>, Runnable>> calls = new ArrayList<>(handling.entrySet());
        for (int i = 0; i < MAX_UNANSWERED_FORWARDS / 2 - 1; i++) {
            calls.get(i).getValue().run();
            channel.runPendingTasks();
        }
        assertThat(isReading(), is(false));
        calls.get(MAX_UNANSWERED_FORWARDS / 2 - 1).getValue().run();
        channel.runPendingTasks();
        assertThat(isReading(), is(true));
        assertThat(reportedToListener, is(sequence(0, MAX_UNANSWERED_FORWARDS / 2)));
    }

    @Test
    public void shouldFailAForwardWithNoResponseWithinMaxSocketTimeoutClosingTheClientConnectionWithOneWarning() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(3);
        upstreamAnswers(1);
        responsesHandled();
        assertThat(repliesWrittenToTheClient(), is(sequence(1, 2)));

        advanceTimeBy(MAX_SOCKET_TIMEOUT_MILLIS - 1);
        assertThat("still waiting just before the timeout", upstreamResponses.get(0).isDone(), is(false));
        assertThat(upstreamResponses.get(2).isDone(), is(false));

        advanceTimeBy(1);
        // completing the response future is what closes its upstream connection
        assertThat("given up at it", upstreamResponses.get(0).isCompletedExceptionally(), is(true));
        assertThat(upstreamResponses.get(2).isCompletedExceptionally(), is(true));
        Throwable timedOut = failureOf(upstreamResponses.get(0));
        assertThat(timedOut, instanceOf(SocketCommunicationException.class));
        assertThat(timedOut.getMessage(), is("no response within " + MAX_SOCKET_TIMEOUT_MILLIS + "ms (maxSocketTimeout, at most maxFutureTimeout)"));
        responsesHandled();
        assertThat("the client connection is closed, as for any failed forward", channel.isOpen(), is(false));

        List<LogEntry> entries = failedForwardEntries();
        assertThat("one warning for each forward given up, none for the answered one", entries, hasSize(2));
        assertThat(entries.get(0).getMessage(configuration()), containsString("no response within " + MAX_SOCKET_TIMEOUT_MILLIS + "ms"));
        assertThat("an upstream that does not answer is named without a stack trace", entries.get(0).getThrowable(), is(nullValue()));
    }

    @Test
    public void shouldWriteAResponseThatArrivedBeforeTheTimeoutEvenIfItIsHandledAfterIt() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(1);
        upstreamAnswers(0);

        advanceTimeBy(MAX_SOCKET_TIMEOUT_MILLIS * 2);
        responsesHandled();

        assertThat(repliesWrittenToTheClient(), is(sequence(0, 1)));
        assertThat(channel.isOpen(), is(true));
        assertThat(failedForwardEntries(), is(empty()));
    }

    @Test
    public void shouldNotGiveUpAForwardOnceItIsAnswered() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(1);
        upstreamAnswers(0);
        responsesHandled();
        assertThat("its give-up is not left scheduled, holding the response", channel.runScheduledPendingTasks(), is(-1L));
    }

    @Test
    public void shouldFailAForwardAfterMaxFutureTimeoutWhenThatIsShorter() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses().maxFutureTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS / 2));
        clientSends(1);

        advanceTimeBy(MAX_SOCKET_TIMEOUT_MILLIS / 2 - 1);
        assertThat(upstreamResponses.get(0).isDone(), is(false));
        advanceTimeBy(1);
        assertThat(upstreamResponses.get(0).isCompletedExceptionally(), is(true));
    }

    @Test
    public void shouldFailAForwardAfterMaxFutureTimeoutWhenMaxSocketTimeoutIsZero() {
        upstreamThatAnswersWhenTold();
        long maxFutureTimeoutMillis = 90_000;
        clientConnection(waitingForResponses().maxSocketTimeoutInMillis(0L).maxFutureTimeoutInMillis(maxFutureTimeoutMillis));
        clientSends(1);

        advanceTimeBy(maxFutureTimeoutMillis - 1);
        assertThat(upstreamResponses.get(0).isDone(), is(false));
        advanceTimeBy(1);
        assertThat(upstreamResponses.get(0).isCompletedExceptionally(), is(true));
    }

    @Test
    public void shouldWaitWithoutLimitWhenMaxSocketTimeoutAndMaxFutureTimeoutAreZero() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses().maxSocketTimeoutInMillis(0L).maxFutureTimeoutInMillis(0L));
        clientSends(1);

        advanceTimeBy(TimeUnit.DAYS.toMillis(1));

        assertThat(upstreamResponses.get(0).isDone(), is(false));
        assertThat(channel.isOpen(), is(true));
    }

    @Test
    public void shouldGiveUpTheHoldWhenTheHandlerIsRemoved() {
        upstreamThatAnswersWhenTold();
        clientConnection(waitingForResponses());
        clientSends(MAX_UNANSWERED_FORWARDS);
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.pipeline().remove(BinaryRequestProxyingHandler.class);
        assertThat(ChannelReadPause.holds(channel), is(0));

        for (int i = 0; i < MAX_UNANSWERED_FORWARDS; i++) {
            upstreamAnswers(i);
        }
        responsesHandled();
        assertThat("and not a second time as the responses are handled", ChannelReadPause.holds(channel), is(0));
    }
}
