package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_PENDING_LISTENER_CALLS;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_UNANSWERED_FORWARDS;

/**
 * With {@code forwardBinaryRequestsWithoutWaitingForResponse}, what one client connection keeps for messages already
 * forwarded is bounded: messages not yet reported to a slower listener hold the client back, and an upstream that
 * never answers keeps at most {@link BinaryRequestProxyingHandler#MAX_UNANSWERED_FORWARDS} of its connections open,
 * each for at most maxSocketTimeoutInMillis.
 */
public class BinaryRequestProxyingHandlerBacklogBoundTest {

    private static final long MAX_SOCKET_TIMEOUT_MILLIS = 5_000;
    private static final BinaryMessage NOT_YET = BinaryMessage.bytes(new byte[]{'?'});

    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    private final MockServerLogger logger = mock(MockServerLogger.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final List<Integer> forwarded = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<BinaryMessage>> upstreamResponses = new CopyOnWriteArrayList<>();
    private final List<Runnable> listenerCallsScheduled = new ArrayList<>();
    private final List<Integer> reportedToListener = new ArrayList<>();
    private final List<BinaryMessage> listenerResponses = new ArrayList<>();
    private EmbeddedChannel channel;
    private int nextMessage;

    @After
    public void releaseChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    /** An upstream that accepts every message at once and never answers one. */
    private void upstreamThatNeverAnswers() {
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any(), any()))
            .thenAnswer(invocation -> {
                forwarded.add(sequenceNumber(invocation.<BinaryMessage>getArgument(0).getBytes()));
                CompletableFuture<BinaryMessage> response = new CompletableFuture<>();
                upstreamResponses.add(response);
                invocation.<Consumer<Throwable>>getArgument(4).accept(null);
                return response;
            });
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
    }

    /** A listener whose calls run only when the test runs them, so it is as slow as the test says. */
    private BinaryProxyListener listenerThatRunsWhenTold() {
        doAnswer(invocation -> listenerCallsScheduled.add(invocation.getArgument(0)))
            .when(scheduler).scheduleLocalCallback(any(Runnable.class), anyBoolean());
        return (binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            reportedToListener.add(sequenceNumber(binaryRequest.getBytes()));
            listenerResponses.add(binaryResponse.getNow(NOT_YET));
        };
    }

    private void clientConnection(Configuration configuration) {
        channel = new EmbeddedChannel();
        // scheduled tasks then run only as the test advances time
        channel.freezeTime();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration, logger, scheduler, httpClient, mock(HttpState.class)));
    }

    private static Configuration notWaitingForResponses() {
        return configuration()
            .forwardBinaryRequestsUseSingleConnection(false)
            .forwardBinaryRequestsWithoutWaitingForResponse(true)
            .maxSocketTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS)
            .maxFutureTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS * 10);
    }

    private void clientSends(int messages) {
        for (int i = 0; i < messages; i++) {
            ByteBuf message = Unpooled.buffer(8).writeInt(nextMessage++).writeInt(0);
            channel.writeInbound(message);
        }
    }

    private static int sequenceNumber(byte[] message) {
        return Unpooled.wrappedBuffer(message).readInt();
    }

    private boolean isReading() {
        return channel.config().isAutoRead() && ChannelReadPause.holds(channel) == 0;
    }

    /** Runs the oldest listener call; the chain then schedules the next. */
    private void listenerReturnsOnce() {
        listenerCallsScheduled.remove(0).run();
        channel.runPendingTasks();
    }

    private List<CompletableFuture<BinaryMessage>> waitingForAResponse() {
        return upstreamResponses.stream().filter(response -> !response.isDone()).collect(Collectors.toList());
    }

    private List<LogEntry> closedWithoutResponseEntries() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues().stream()
            .filter(entry -> entry.getMessageFormat() != null && entry.getMessageFormat().startsWith("closing the upstream connection to:{}of a forwarded binary request without its response"))
            .collect(Collectors.toList());
    }

    @Test
    public void shouldStopReadingTheClientOnlyOnceMoreListenerCallsWaitThanTheLimit() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses().binaryProxyListener(listenerThatRunsWhenTold()));

        clientSends(MAX_PENDING_LISTENER_CALLS);
        assertThat("at the limit the client is still read", isReading(), is(true));

        clientSends(1);
        assertThat("one more than the limit and it is not", isReading(), is(false));
        assertThat("each message is forwarded all the same", forwarded, hasSize(MAX_PENDING_LISTENER_CALLS + 1));

        // what a read already under way still delivers
        clientSends(2);
        assertThat("the hold is taken once, however many more arrive", ChannelReadPause.holds(channel), is(1));
    }

    @Test
    public void shouldReadTheClientAgainOnceHalfOfTheListenerCallsHaveReturnedLosingNothing() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses().binaryProxyListener(listenerThatRunsWhenTold()));
        clientSends(MAX_PENDING_LISTENER_CALLS + 1);
        assertThat(isReading(), is(false));

        for (int pending = MAX_PENDING_LISTENER_CALLS + 1; pending > MAX_PENDING_LISTENER_CALLS / 2 + 1; pending--) {
            listenerReturnsOnce();
            assertThat("still not read with " + (pending - 1) + " listener calls waiting", isReading(), is(false));
        }
        listenerReturnsOnce();
        assertThat("read again with " + MAX_PENDING_LISTENER_CALLS / 2 + " waiting", isReading(), is(true));

        clientSends(3);
        while (!listenerCallsScheduled.isEmpty()) {
            listenerReturnsOnce();
        }
        List<Integer> everyMessage = new ArrayList<>();
        for (int i = 0; i < nextMessage; i++) {
            everyMessage.add(i);
        }
        assertThat("every message is reported to the listener, in order", reportedToListener, is(everyMessage));
        assertThat("and forwarded, in order", forwarded, is(everyMessage));
        assertThat(isReading(), is(true));
    }

    @Test
    public void shouldGiveUpTheListenerHoldWhenTheHandlerIsRemoved() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses().binaryProxyListener(listenerThatRunsWhenTold()));
        clientSends(MAX_PENDING_LISTENER_CALLS + 1);
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.pipeline().remove(BinaryRequestProxyingHandler.class);
        assertThat(ChannelReadPause.holds(channel), is(0));

        while (!listenerCallsScheduled.isEmpty()) {
            listenerReturnsOnce();
        }
        assertThat("and not a second time as the listener catches up", ChannelReadPause.holds(channel), is(0));
        assertThat(reportedToListener, hasSize(MAX_PENDING_LISTENER_CALLS + 1));
    }

    @Test
    public void shouldNotHoldBackAClientWithoutAListener() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses());

        clientSends(MAX_PENDING_LISTENER_CALLS * 3);

        assertThat(isReading(), is(true));
        assertThat(forwarded, hasSize(MAX_PENDING_LISTENER_CALLS * 3));
    }

    @Test
    public void shouldCloseTheOldestUnansweredForwardOnceMoreThanTheLimitWait() throws Exception {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses());

        clientSends(MAX_UNANSWERED_FORWARDS);
        assertThat("at the limit every forward still waits", waitingForAResponse(), hasSize(MAX_UNANSWERED_FORWARDS));
        assertThat(closedWithoutResponseEntries(), is(empty()));

        clientSends(1);
        assertThat("one more and the oldest is given up", upstreamResponses.get(0).isDone(), is(true));
        assertThat("as if the upstream had closed it without answering", upstreamResponses.get(0).get(), is(nullValue()));
        assertThat(waitingForAResponse(), hasSize(MAX_UNANSWERED_FORWARDS));

        clientSends(MAX_UNANSWERED_FORWARDS * 4);
        assertThat("however many follow", waitingForAResponse(), hasSize(MAX_UNANSWERED_FORWARDS));
        assertThat("the oldest go first", waitingForAResponse(), is(upstreamResponses.subList(upstreamResponses.size() - MAX_UNANSWERED_FORWARDS, upstreamResponses.size())));
        assertThat("nothing was lost on the way upstream", forwarded, hasSize(nextMessage));
        assertThat("the client is never held back for it", isReading(), is(true));
        assertThat("nor is its connection closed", channel.isOpen(), is(true));
        assertThat("nor anything written to it", (Object) channel.readOutbound(), is(nullValue()));

        List<LogEntry> entries = closedWithoutResponseEntries();
        assertThat("one entry for each given up", entries, hasSize(nextMessage - MAX_UNANSWERED_FORWARDS));
        assertThat(entries.get(0).getLogLevel(), is(Level.INFO));
        assertThat(entries.get(0).getMessageFormat(), is("closing the upstream connection to:{}of a forwarded binary request without its response, because{}more recent binary requests on the same connection are waiting for theirs"));
        assertThat(entries.get(0).getArguments()[1], is(MAX_UNANSWERED_FORWARDS));
    }

    @Test
    public void shouldNotCountAForwardOnceItIsAnswered() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses());

        clientSends(MAX_UNANSWERED_FORWARDS);
        upstreamResponses.get(10).complete(BinaryMessage.bytes(new byte[]{1}));
        channel.runPendingTasks();
        clientSends(1);

        assertThat("an answered forward leaves room for one more", waitingForAResponse(), hasSize(MAX_UNANSWERED_FORWARDS));
        assertThat("so none is given up", closedWithoutResponseEntries(), is(empty()));
    }

    @Test
    public void shouldCloseAnUnansweredForwardAfterMaxSocketTimeout() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses());

        clientSends(3);
        upstreamResponses.get(1).complete(BinaryMessage.bytes(new byte[]{1}));
        channel.runPendingTasks();

        channel.advanceTimeBy(MAX_SOCKET_TIMEOUT_MILLIS - 1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat("still waiting just before the timeout", waitingForAResponse(), hasSize(2));

        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat("given up at it", waitingForAResponse(), is(empty()));
        assertThat(upstreamResponses.get(0).getNow(NOT_YET), is(nullValue()));
        assertThat("the client connection stays open", channel.isOpen(), is(true));

        List<LogEntry> entries = closedWithoutResponseEntries();
        assertThat("one entry for each unanswered forward, none for the answered one", entries, hasSize(2));
        assertThat(entries.get(0).getMessageFormat(), is("closing the upstream connection to:{}of a forwarded binary request without its response, because none arrived within{}ms (maxSocketTimeout, at most maxFutureTimeout)"));
        assertThat(entries.get(0).getArguments()[1], is(MAX_SOCKET_TIMEOUT_MILLIS));
    }

    @Test
    public void shouldCloseAnUnansweredForwardAfterMaxFutureTimeoutWhenThatIsShorter() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses().maxFutureTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS / 2));

        clientSends(1);
        channel.advanceTimeBy(MAX_SOCKET_TIMEOUT_MILLIS / 2 - 1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(waitingForAResponse(), hasSize(1));

        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(waitingForAResponse(), is(empty()));
        assertThat(closedWithoutResponseEntries().get(0).getArguments()[1], is(MAX_SOCKET_TIMEOUT_MILLIS / 2));
    }

    @Test
    public void shouldCloseAnUnansweredForwardAfterMaxFutureTimeoutWhenMaxSocketTimeoutIsZero() {
        upstreamThatNeverAnswers();
        long maxFutureTimeoutMillis = 90_000;
        clientConnection(notWaitingForResponses().maxSocketTimeoutInMillis(0L).maxFutureTimeoutInMillis(maxFutureTimeoutMillis));

        clientSends(1);
        channel.advanceTimeBy(maxFutureTimeoutMillis - 1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(waitingForAResponse(), hasSize(1));

        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(waitingForAResponse(), is(empty()));
    }

    @Test
    public void shouldGiveTheListenerNoResponseForAForwardThatIsGivenUp() {
        upstreamThatNeverAnswers();
        clientConnection(notWaitingForResponses().binaryProxyListener(listenerThatRunsWhenTold()));

        clientSends(MAX_UNANSWERED_FORWARDS + 1);
        listenerReturnsOnce();
        listenerReturnsOnce();

        assertThat(reportedToListener, contains(0, 1));
        assertThat("the given-up forward's response is empty, as for an upstream that closes without answering", listenerResponses.get(0), is(nullValue()));
        assertThat("the next is still waiting", listenerResponses.get(1), is(sameInstance(NOT_YET)));
    }
}
