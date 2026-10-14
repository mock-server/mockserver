package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.util.AttributeKey;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.HeaderLimitExceededException;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketCommunicationException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.SensitiveLogValue;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryMessage;
import org.mockserver.model.BinaryProxyListener;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.BinaryResponse;
import org.mockserver.model.Delay;
import org.mockserver.netty.proxy.relay.BinaryRelay;
import org.mockserver.netty.proxy.relay.UpstreamReply;
import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.SocketAddresses;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.function.LongConsumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.exception.ExceptionHandling.boundedFault;
import static org.mockserver.exception.ExceptionHandling.boundedFaultMessage;
import static org.mockserver.exception.ExceptionHandling.closeOnFlush;
import static org.mockserver.exception.ExceptionHandling.connectionClosedException;
import static org.mockserver.exception.ExceptionHandling.isSslOrDecoderFault;
import static org.mockserver.exception.ExceptionHandling.sniDescription;
import static org.mockserver.exception.ExceptionHandling.upstreamConnectionFailure;
import static org.mockserver.formatting.StringFormatter.formatBytes;
import static org.mockserver.formatting.StringFormatter.hexDumpForLog;
import static org.mockserver.formatting.StringFormatter.utf8ForLog;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static org.mockserver.model.BinaryMessage.bytes;
import static org.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class BinaryRequestProxyingHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final AttributeKey<ForwardQueue> FORWARD_QUEUE = AttributeKey.valueOf("BINARY_FORWARD_QUEUE");
    public static final AttributeKey<CompletableFuture<Void>> PREVIOUS_LISTENER_CALL = AttributeKey.valueOf("PREVIOUS_BINARY_PROXY_LISTENER_CALL");
    private static final AttributeKey<Boolean> EXPECTATIONS_NOT_MATCHED_WARNED = AttributeKey.valueOf("BINARY_EXPECTATIONS_NOT_MATCHED_WARNED");
    private static final AttributeKey<Boolean> UNTRACKED_REPLIES_WARNED = AttributeKey.valueOf("BINARY_UNTRACKED_REPLIES_WARNED");
    private static final AttributeKey<Boolean> USE_SINGLE_CONNECTION = AttributeKey.valueOf("BINARY_USE_SINGLE_CONNECTION");
    /**
     * The client connection is not read while more than either of these wait to be forwarded, and is read again
     * once no more than half of each do. The queue only has to absorb what a client sends while one message is
     * connected and written; a client that stays ahead of that is slowed down rather than held in memory.
     */
    static final int MAX_WAITING_MESSAGES = 64;
    static final int MAX_WAITING_BYTES = 256 * 1024;
    /**
     * The client connection is also not read while more messages than this wait to be reported to the listener, and
     * is read again once no more than half do, so a listener slower than the client slows the client down.
     */
    static final int MAX_PENDING_LISTENER_CALLS = 64;
    /**
     * The most forwarded messages of one client connection that keep their upstream connection open waiting for a
     * response: forwarding one more closes the oldest, which then gets no response, as if the upstream had closed it.
     * Half the listener limit, so a listener that waits for each response, from an upstream that sends none, has its
     * wait ended before the client is held back for it, and a client held back is read again. Waiting for responses,
     * a client with this many not yet answered is not read instead, so that no response it waits for is lost.
     */
    static final int MAX_UNANSWERED_FORWARDS = MAX_PENDING_LISTENER_CALLS / 2;

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final Scheduler scheduler;
    private final NettyHttpClient httpClient;
    private final BinaryProxyListener binaryExchangeCallback;
    private final HttpState httpState;

    public BinaryRequestProxyingHandler(final Configuration configuration, final MockServerLogger mockServerLogger, final Scheduler scheduler, final NettyHttpClient httpClient, final HttpState httpState) {
        super(true);
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
        this.binaryExchangeCallback = configuration.binaryProxyListener();
        this.httpState = httpState;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        if (BinaryLocalReplies.closing(ctx.channel())) {
            // the connection closes once the replies before this message are written, so this one is not answered
            return;
        }
        BinaryMessage binaryRequest = bytes(ByteBufUtil.getBytes(byteBuf));
        String logCorrelationId = UUIDService.getNonSecureUUID();
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
                .setLogLevel(Level.INFO)
                .setCorrelationId(logCorrelationId)
                .setMessageFormat("received binary request:{}")
                .setArguments(SensitiveLogValue.of(hexDumpForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
        );
        final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
        if (remoteAddress != null) {
            if (!answeredByExpectation(ctx, binaryRequest, logCorrelationId, remoteAddress)) {
                sendMessage(ctx, binaryRequest, logCorrelationId, remoteAddress);
            }
        } else if (httpState != null) {
            Expectation matchedExpectation = firstMatchingBinaryExpectation(binaryRequest, logCorrelationId);
            if (matchedExpectation != null && matchedExpectation.getBinaryResponse() != null) {
                replyFromExpectation(ctx, matchedExpectation, binaryRequest, logCorrelationId, null);
            } else {
                if (matchedExpectation != null) {
                    httpState.postProcess(matchedExpectation);
                }
                if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("no matching binary expectation for binary request:{}")
                            .setArguments(SensitiveLogValue.of(hexDumpForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
                    );
                }
                writeUnknownFormatMessage(ctx, binaryRequest, logCorrelationId);
            }
        } else {
            writeUnknownFormatMessage(ctx, binaryRequest, logCorrelationId);
        }
    }

    private Expectation firstMatchingBinaryExpectation(BinaryMessage binaryRequest, String logCorrelationId) {
        BinaryRequestDefinition binaryRequestDefinition = BinaryRequestDefinition.binaryRequest(binaryRequest.getBytes());
        binaryRequestDefinition.withLogCorrelationId(logCorrelationId);
        return httpState.firstMatchingExpectation(binaryRequestDefinition);
    }

    /**
     * Post-processes a matched expectation, which removes one that its Times have used up, and writes its binary
     * response if that has data: after the response's delay, and after every earlier reply on the connection.
     *
     * @param afterWrite run once the response has been written, or null
     */
    private void replyFromExpectation(ChannelHandlerContext ctx, Expectation matchedExpectation, BinaryMessage binaryRequest, String logCorrelationId, Runnable afterWrite) {
        // before the write, so a client that has its reply finds the expectation's state already updated
        httpState.postProcess(matchedExpectation);
        BinaryResponse binaryResponse = matchedExpectation.getBinaryResponse();
        byte[] reply = binaryResponse.getBinaryData();
        if (reply == null || reply.length == 0) {
            // a message that has no reply. Null and empty mean the same: an empty array is not serialised, so one
            // set through the Java client arrives as null, while raw JSON can still deliver it empty
            if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(FORWARDED_REQUEST)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("returning nothing, as the binary mock response is empty, for binary request:{}")
                        .setArguments(SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
                );
            }
        } else {
            if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(FORWARDED_REQUEST)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("returning binary mock response:{}for binary request:{}")
                        .setArguments(SensitiveLogValue.of(formatBytes(reply, configuration.maxLoggedBodyBytes())), SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
                );
            }
            Delay delay = binaryResponse.getDelay();
            BinaryLocalReplies.write(ctx, reply, delay != null ? delay.sampleValueMillis() : 0, false, afterWrite);
        }
    }

    /**
     * With forwardBinaryRequestsMatchExpectations, a message on a connection relayed on one upstream connection
     * whose bytes match a binary expectation is answered here and not forwarded, or, as the binary response's
     * {@code upstream} says, answered and forwarded or forwarded with its reply replaced. A message no expectation
     * can match is forwarded without a matcher pass, so it adds no match entries to the log. Any other connection is
     * forwarded as without the setting, and says so once.
     *
     * @return whether the message was handled here, so is not to be forwarded by the caller
     */
    private boolean answeredByExpectation(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
        if (httpState == null || !configuration.forwardBinaryRequestsMatchExpectations()) {
            return false;
        }
        if (!usesSingleConnection(ctx)
            || !BinaryRelay.relaysOnOneConnection(ctx, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback)) {
            warnOnceThatExpectationsAreNotMatched(ctx, remoteAddress, logCorrelationId);
            return false;
        }
        if (!httpState.mayMatchBinaryMessage(binaryRequest.getBytes())) {
            return false;
        }
        Expectation matchedExpectation = firstMatchingBinaryExpectation(binaryRequest, logCorrelationId);
        if (matchedExpectation == null) {
            return false;
        }
        if (matchedExpectation.getBinaryResponse() == null) {
            httpState.postProcess(matchedExpectation);
            if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("forwarding binary request:{}to:{}because the expectation it matched has no binary response:{}")
                        .setArguments(SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())), remoteAddress, matchedExpectation.getId())
                );
            }
            return false;
        }
        BinaryResponse binaryResponse = matchedExpectation.getBinaryResponse();
        switch (upstreamOf(ctx, matchedExpectation, logCorrelationId)) {
            case ANSWER_AND_FORWARD:
                // answered first, so a reply written now is checked against the message forwarded before this one
                replyFromExpectation(ctx, matchedExpectation, binaryRequest, logCorrelationId, () -> BinaryRelay.answeredLocally(ctx.channel(), logCorrelationId));
                BinaryRelay.forward(ctx, binaryRequest, logCorrelationId, UpstreamReply.drop(), remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
                break;
            case FORWARD_AND_REPLACE:
                httpState.postProcess(matchedExpectation);
                if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("forwarding binary request:{}to:{}to write binary mock response:{}in place of its response")
                            .setArguments(SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())), remoteAddress, SensitiveLogValue.of(formatBytes(binaryResponse.getBinaryData() != null ? binaryResponse.getBinaryData() : new byte[0], configuration.maxLoggedBodyBytes())))
                    );
                }
                Delay delay = binaryResponse.getDelay();
                UpstreamReply replaced = UpstreamReply.replaceWith(binaryResponse.getBinaryData(), delay != null ? delay.sampleValueMillis() : 0);
                BinaryRelay.forward(ctx, binaryRequest, logCorrelationId, replaced, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
                break;
            default:
                replyFromExpectation(ctx, matchedExpectation, binaryRequest, logCorrelationId, () -> BinaryRelay.answeredLocally(ctx.channel(), logCorrelationId));
        }
        return true;
    }

    /**
     * Opens the upstream connection of a connection whose client has sent nothing, for a server that speaks first
     * (forwardBinaryServerFirstWaitMillis). Must be called on the connection's event loop.
     *
     * @return false, having opened nothing, for a connection with no target or one not relayed on one upstream
     * connection
     */
    public boolean connectBeforeClientSpeaks(ChannelHandlerContext ctx) {
        InetSocketAddress remoteAddress = getRemoteAddress(ctx);
        return remoteAddress != null
            && usesSingleConnection(ctx)
            && BinaryRelay.openBeforeClientSpeaks(ctx, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
    }

    /**
     * The matched expectation's {@code upstream}, or ANSWER_ONLY where the upstream's reply to a message cannot be told
     * apart: a connection without a protocol's framing, said once per connection.
     */
    private BinaryResponse.Upstream upstreamOf(ChannelHandlerContext ctx, Expectation matchedExpectation, String logCorrelationId) {
        BinaryResponse.Upstream upstream = matchedExpectation.getBinaryResponse().getUpstream();
        if (upstream == null || upstream == BinaryResponse.Upstream.ANSWER_ONLY) {
            return BinaryResponse.Upstream.ANSWER_ONLY;
        }
        if (BinaryRelay.tracksReplies(ctx.channel())) {
            return upstream;
        }
        if (ctx.channel().attr(UNTRACKED_REPLIES_WARNED).setIfAbsent(Boolean.TRUE) == null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("answering binary connection from:{}as ANSWER_ONLY where binary expectation:{}asks for:{}because nothing says where the upstream's reply to a message ends on it; that needs binaryMessageFraming POSTGRESQL")
                    .setArguments(ctx.channel().remoteAddress(), matchedExpectation.getId(), upstream)
            );
        }
        return BinaryResponse.Upstream.ANSWER_ONLY;
    }

    /**
     * forwardBinaryRequestsUseSingleConnection as it was at the connection's first message: a connection is relayed
     * or forwarded one message per upstream connection for its whole life.
     */
    private boolean usesSingleConnection(ChannelHandlerContext ctx) {
        Boolean pinned = ctx.channel().attr(USE_SINGLE_CONNECTION).get();
        if (pinned == null) {
            pinned = Boolean.TRUE.equals(configuration.forwardBinaryRequestsUseSingleConnection());
            ctx.channel().attr(USE_SINGLE_CONNECTION).set(pinned);
        }
        return pinned;
    }

    private void warnOnceThatExpectationsAreNotMatched(ChannelHandlerContext ctx, InetSocketAddress remoteAddress, String logCorrelationId) {
        if (ctx.channel().attr(EXPECTATIONS_NOT_MATCHED_WARNED).setIfAbsent(Boolean.TRUE) == null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("binary expectations are not matched on binary connection from:{}to:{}because its messages are each forwarded on an upstream connection of their own; forwardBinaryRequestsMatchExpectations applies only to a connection relayed on one (forwardBinaryRequestsUseSingleConnection)")
                    .setArguments(ctx.channel().remoteAddress(), remoteAddress)
            );
        }
    }

    private void writeUnknownFormatMessage(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId) {
        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat(
                        "unknown message format, only HTTP requests are supported for mocking or HTTP & binary requests for proxying, but request is not being proxied and request is not valid HTTP, found request in binary: {} in utf8 text: {}"
                    )
                    .setArguments(SensitiveLogValue.of(hexDumpForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())), SensitiveLogValue.of(utf8ForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
            );
        }
        // behind any reply still waiting for its delay, which is written before the connection closes
        BinaryLocalReplies.write(ctx,
            "unknown message format, only HTTP requests are supported for mocking or HTTP & binary requests for proxying, but request is not being proxied and request is not valid HTTP".getBytes(StandardCharsets.UTF_8),
            0, true, null);
    }

    private void sendMessage(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
        if (usesSingleConnection(ctx)
            && BinaryRelay.forward(ctx, binaryRequest, logCorrelationId, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback)) {
            return;
        }
        if (configuration.forwardBinaryRequestsWithoutWaitingForResponse()) {
            ForwardQueue queue = forwardQueue(ctx);
            CompletableFuture<BinaryMessage> binaryResponseFuture = sendInArrivalOrder(ctx, queue, binaryRequest, logCorrelationId, remoteAddress);
            processNotWaitingForResponse(ctx, queue, binaryRequest, logCorrelationId, remoteAddress, binaryResponseFuture);
        } else {
            forwardWaitingForResponse(ctx, binaryRequest, logCorrelationId, remoteAddress);
        }
    }

    /**
     * Waiting for responses, each message is forwarded as it is read, on an upstream connection of its own. The client
     * is not read while {@link #MAX_UNANSWERED_FORWARDS} of its forwards are not yet answered and handled, and is read
     * again once no more than half are. A forward with no response within the response timeout fails, which closes
     * the client connection, as any failed forward in this mode does.
     */
    private void forwardWaitingForResponse(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
        CompletableFuture<BinaryMessage> binaryResponseFuture = httpClient
            .sendRequest(
                binaryRequest,
                isSslEnabledUpstream(ctx.channel()),
                remoteAddress,
                configuration.socketConnectionTimeoutInMillis()
            );
        ForwardQueue queue = forwardQueue(ctx);
        queue.awaitingResponse++;
        pauseReadsIfBehind(ctx, queue);
        // completing the future, if the response has not done so first, closes its upstream connection
        ScheduledFuture<?> timeout = afterResponseTimeout(ctx, timeoutMillis -> binaryResponseFuture.completeExceptionally(
            new SocketCommunicationException("no response within " + timeoutMillis + "ms (maxSocketTimeout, at most maxFutureTimeout)", null)
        ));
        processWaitingForResponse(ctx, binaryRequest, logCorrelationId, remoteAddress, binaryResponseFuture, () -> onEventLoop(ctx, () -> {
            if (timeout != null) {
                timeout.cancel(false);
            }
            queue.awaitingResponse--;
            resumeReadsOnceDrained(ctx, queue);
        }));
    }

    /**
     * Without waiting for responses a client can send its next message at once, and each message is forwarded on
     * a connection of its own, so the next is only started once the previous has been written: otherwise the
     * upstream could accept the two in either order. Once a forward fails the messages behind it are not
     * attempted, as the connection is then closed. A client that has sent its messages and closed still has
     * every one of them forwarded.
     */
    private static ForwardQueue forwardQueue(ChannelHandlerContext ctx) {
        ForwardQueue queue = ctx.channel().attr(FORWARD_QUEUE).get();
        if (queue == null) {
            queue = new ForwardQueue();
            ctx.channel().attr(FORWARD_QUEUE).set(queue);
        }
        return queue;
    }

    private CompletableFuture<BinaryMessage> sendInArrivalOrder(ChannelHandlerContext ctx, ForwardQueue queue, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
        QueuedForward forward = new QueuedForward(binaryRequest, logCorrelationId, remoteAddress, isSslEnabledUpstream(ctx.channel()));
        queue.waiting.add(forward);
        queue.waitingBytes += binaryRequest.getBytes().length;
        startWaitingForwards(ctx, queue);
        pauseReadsIfBehind(ctx, queue);
        return forward.response;
    }

    /**
     * Also while the client connection is not writable: a client that does not read its replies is not read either, so
     * replies to it cannot pile up beyond what the forwards already started bring.
     */
    private static void pauseReadsIfBehind(ChannelHandlerContext ctx, ForwardQueue queue) {
        if (!queue.readsPaused && (queue.waiting.size() > MAX_WAITING_MESSAGES || queue.waitingBytes > MAX_WAITING_BYTES || queue.pendingListenerCalls > MAX_PENDING_LISTENER_CALLS || queue.awaitingResponse >= MAX_UNANSWERED_FORWARDS || !ctx.channel().isWritable())) {
            queue.readsPaused = true;
            ChannelReadPause.pause(ctx.channel());
        }
    }

    private static void resumeReadsOnceDrained(ChannelHandlerContext ctx, ForwardQueue queue) {
        if (queue.readsPaused && queue.waiting.size() <= MAX_WAITING_MESSAGES / 2 && queue.waitingBytes <= MAX_WAITING_BYTES / 2 && queue.pendingListenerCalls <= MAX_PENDING_LISTENER_CALLS / 2 && queue.awaitingResponse <= MAX_UNANSWERED_FORWARDS / 2 && ctx.channel().isWritable()) {
            queue.readsPaused = false;
            ChannelReadPause.resume(ctx.channel());
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        // nothing read from now on reaches this handler, so it has no reason left to hold reads back
        BinaryLocalReplies.discard(ctx.channel());
        ForwardQueue queue = ctx.channel().attr(FORWARD_QUEUE).get();
        if (queue != null && queue.readsPaused) {
            queue.readsPaused = false;
            ChannelReadPause.resume(ctx.channel());
        }
    }

    private void startWaitingForwards(ChannelHandlerContext ctx, ForwardQueue queue) {
        if (queue.starting) {
            // called back from inside sendRequest: the loop below carries on, so a long queue does not recurse
            return;
        }
        queue.starting = true;
        try {
            int notForwarded = 0;
            InetSocketAddress remoteAddress = null;
            QueuedForward forward;
            while (!queue.inFlight && (forward = queue.waiting.poll()) != null) {
                queue.waitingBytes -= forward.request.getBytes().length;
                if (queue.failed) {
                    forward.response.completeExceptionally(new NotForwardedException());
                    remoteAddress = forward.remoteAddress;
                    notForwarded++;
                } else {
                    start(ctx, queue, forward);
                }
            }
            if (notForwarded > 0 && mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("not forwarding{}binary message(s) to{}because an earlier message on the same connection could not be forwarded")
                        .setArguments(notForwarded, remoteAddress)
                );
            }
        } finally {
            queue.starting = false;
            resumeReadsOnceDrained(ctx, queue);
        }
    }

    private void start(ChannelHandlerContext ctx, ForwardQueue queue, QueuedForward forward) {
        queue.inFlight = true;
        while (queue.unanswered.size() >= MAX_UNANSWERED_FORWARDS) {
            Unanswered oldest = queue.unanswered.peekFirst();
            if (giveUp(queue, oldest) && mockServerLogger.isEnabledForInstance(Level.INFO)) {
                mockServerLogger.logEvent(
                    givenUpEntry(oldest)
                        .setMessageFormat("closing the upstream connection to:{}of a forwarded binary request without its response, because{}more recent binary requests on the same connection are waiting for theirs")
                        .setArguments(oldest.remoteAddress, MAX_UNANSWERED_FORWARDS)
                );
            }
        }
        Unanswered unanswered = new Unanswered(forward.logCorrelationId, forward.remoteAddress);
        queue.unanswered.addLast(unanswered);
        try {
            unanswered.upstream = httpClient
                .sendRequest(forward.request, forward.secure, forward.remoteAddress, configuration.socketConnectionTimeoutInMillis(), sendFailure -> onEventLoop(ctx, () -> {
                    queue.inFlight = false;
                    if (sendFailure != null) {
                        queue.failed = true;
                        // a request that was not written gets no response; without this it would complete empty
                        forward.response.completeExceptionally(sendFailure);
                    } else {
                        stopWaitingAfterMaxSocketTimeout(ctx, queue, unanswered);
                    }
                    startWaitingForwards(ctx, queue);
                }));
            unanswered.upstream
                .whenComplete((binaryResponse, throwable) -> {
                    if (throwable != null) {
                        forward.response.completeExceptionally(throwable);
                        onEventLoop(ctx, () -> {
                            answered(queue, unanswered);
                            queue.failed = true;
                            startWaitingForwards(ctx, queue);
                        });
                    } else {
                        forward.response.complete(binaryResponse);
                        onEventLoop(ctx, () -> answered(queue, unanswered));
                    }
                });
        } catch (RuntimeException cannotStart) {
            answered(queue, unanswered);
            forward.response.completeExceptionally(cannotStart);
            queue.inFlight = false;
            queue.failed = true;
        }
    }

    /**
     * A forwarded message that has had no response within maxSocketTimeoutInMillis of being written is given up, as
     * an HTTP forward is, so an upstream that keeps connections open and never answers does not keep them all.
     */
    private void stopWaitingAfterMaxSocketTimeout(ChannelHandlerContext ctx, ForwardQueue queue, Unanswered unanswered) {
        if (queue.unanswered.contains(unanswered)) {
            unanswered.timeout = afterResponseTimeout(ctx, afterMillis -> {
                if (giveUp(queue, unanswered) && mockServerLogger.isEnabledForInstance(Level.INFO)) {
                    mockServerLogger.logEvent(
                        givenUpEntry(unanswered)
                            .setMessageFormat("closing the upstream connection to:{}of a forwarded binary request without its response, because none arrived within{}ms (maxSocketTimeout, at most maxFutureTimeout)")
                            .setArguments(unanswered.remoteAddress, afterMillis)
                    );
                }
            });
        }
    }

    /**
     * Runs {@code giveUp}, on the client connection's event loop, once a forwarded message has waited as long for its
     * response as an HTTP forward would: maxSocketTimeoutInMillis, or maxFutureTimeoutInMillis where that is shorter
     * or maxSocketTimeoutInMillis is 0.
     *
     * @return the scheduled give-up, or null where both are 0 or the event loop has shut down
     */
    private ScheduledFuture<?> afterResponseTimeout(ChannelHandlerContext ctx, LongConsumer giveUp) {
        long timeoutMillis = configuration.maxFutureTimeoutInMillis();
        long maxSocketTimeoutInMillis = configuration.maxSocketTimeoutInMillis();
        if (maxSocketTimeoutInMillis > 0 && maxSocketTimeoutInMillis < timeoutMillis) {
            timeoutMillis = maxSocketTimeoutInMillis;
        }
        if (timeoutMillis <= 0) {
            return null;
        }
        long afterMillis = timeoutMillis;
        try {
            return ctx.executor().schedule(() -> giveUp.accept(afterMillis), afterMillis, MILLISECONDS);
        } catch (RejectedExecutionException serverStopping) {
            // the event loop has shut down, and the forward client's connections with it
            return null;
        }
    }

    /**
     * Completes the forward with no response, as an upstream that closes without answering does in this mode, which
     * closes its upstream connection. Must be called on the client connection's event loop.
     *
     * @return false, having completed nothing, for a forward answered or failed since it was chosen
     */
    private static boolean giveUp(ForwardQueue queue, Unanswered unanswered) {
        if (!answered(queue, unanswered) || unanswered.upstream == null || unanswered.upstream.isDone()) {
            return false;
        }
        return unanswered.upstream.complete(null);
    }

    private static LogEntry givenUpEntry(Unanswered unanswered) {
        return new LogEntry()
            .setLogLevel(Level.INFO)
            .setCorrelationId(unanswered.logCorrelationId);
    }

    /**
     * @return whether it was still waiting
     */
    private static boolean answered(ForwardQueue queue, Unanswered unanswered) {
        if (!queue.unanswered.remove(unanswered)) {
            return false;
        }
        if (unanswered.timeout != null) {
            unanswered.timeout.cancel(false);
        }
        return true;
    }

    private static void onEventLoop(ChannelHandlerContext ctx, Runnable task) {
        if (ctx.executor().inEventLoop()) {
            task.run();
        } else {
            try {
                ctx.executor().execute(task);
            } catch (RejectedExecutionException serverStopping) {
                // the event loop has shut down, so nothing more can be forwarded for this connection
            }
        }
    }

    /**
     * One client connection's messages waiting for the one being forwarded. Used only on that connection's event
     * loop. Bounded by not reading the connection while it is full, so it can exceed its limits only by the
     * message that takes it past them and what was read with that message.
     */
    private static final class ForwardQueue {
        private final Deque<QueuedForward> waiting = new ArrayDeque<>();
        /** Forwarded, oldest first, each holding its upstream connection open until it is answered or given up. */
        private final Deque<Unanswered> unanswered = new ArrayDeque<>();
        private long waitingBytes;
        private int pendingListenerCalls;
        /** Forwarded waiting for a response, not yet answered, or answered and not yet written to the client. */
        private int awaitingResponse;
        private boolean readsPaused;
        private boolean inFlight;
        private boolean failed;
        private boolean starting;
    }

    private static final class QueuedForward {
        private final BinaryMessage request;
        private final String logCorrelationId;
        private final InetSocketAddress remoteAddress;
        private final boolean secure;
        private final CompletableFuture<BinaryMessage> response = new CompletableFuture<>();

        private QueuedForward(BinaryMessage request, String logCorrelationId, InetSocketAddress remoteAddress, boolean secure) {
            this.request = request;
            this.logCorrelationId = logCorrelationId;
            this.remoteAddress = remoteAddress;
            this.secure = secure;
        }
    }

    /**
     * A forwarded message waiting for its response. The callbacks on its response keep its {@link QueuedForward}, and
     * so its bytes, until then: at most {@link #MAX_UNANSWERED_FORWARDS} gathered messages per client connection.
     */
    private static final class Unanswered {
        private final String logCorrelationId;
        private final InetSocketAddress remoteAddress;
        private CompletableFuture<BinaryMessage> upstream;
        private ScheduledFuture<?> timeout;

        private Unanswered(String logCorrelationId, InetSocketAddress remoteAddress) {
            this.logCorrelationId = logCorrelationId;
            this.remoteAddress = remoteAddress;
        }
    }

    /**
     * Fails the response of a message that was not forwarded because an earlier one on its connection failed.
     */
    static final class NotForwardedException extends RuntimeException {
        NotForwardedException() {
            super("not forwarded because an earlier message on the same connection could not be forwarded", null, false, false);
        }
    }

    /**
     * The listener is the user's code and may wait on the response, so it runs off the event loop, which would
     * otherwise forward nothing more until it returned. One connection's messages are still reported one at a
     * time, in arrival order, and a listener that throws still closes the connection. Those not yet reported count
     * towards the client connection's read pause.
     */
    private void notifyListener(ChannelHandlerContext ctx, ForwardQueue queue, BinaryMessage binaryRequest, CompletableFuture<BinaryMessage> binaryResponseFuture, InetSocketAddress remoteAddress) {
        SocketAddress clientAddress = SocketAddresses.clientAddress(ctx.channel());
        queue.pendingListenerCalls++;
        pauseReadsIfBehind(ctx, queue);
        CompletableFuture<Void> called = new CompletableFuture<>();
        CompletableFuture<Void> previousCalled = ctx.channel().attr(PREVIOUS_LISTENER_CALL).getAndSet(called);
        Runnable call = () -> scheduler.scheduleLocalCallback(() -> {
            Throwable thrown = null;
            try {
                binaryExchangeCallback.onProxy(binaryRequest, binaryResponseFuture, remoteAddress, clientAddress);
            } catch (Throwable throwable) {
                thrown = throwable;
            } finally {
                called.complete(null);
                Throwable failure = thrown;
                onEventLoop(ctx, () -> {
                    queue.pendingListenerCalls--;
                    resumeReadsOnceDrained(ctx, queue);
                    if (failure != null) {
                        exceptionCaught(ctx, failure);
                    }
                });
            }
        }, false);
        if (previousCalled == null) {
            call.run();
        } else {
            previousCalled.thenRun(call);
        }
    }

    private void processNotWaitingForResponse(ChannelHandlerContext ctx, ForwardQueue queue, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress, CompletableFuture<BinaryMessage> binaryResponseFuture) {
        if (binaryExchangeCallback != null) {
            notifyListener(ctx, queue, binaryRequest, binaryResponseFuture, remoteAddress);
        }
        scheduler.submit(binaryResponseFuture, () -> {
            try {
                BinaryMessage binaryResponse = binaryResponseFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
                if (binaryResponse != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(FORWARDED_REQUEST)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("returning binary response:{}from:{}for forwarded binary request:{}")
                            .setArguments(SensitiveLogValue.of(formatBytes(binaryResponse.getBytes(), configuration.maxLoggedBodyBytes())), remoteAddress, SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
                    );
                    ctx.writeAndFlush(Unpooled.copiedBuffer(binaryResponse.getBytes()));
                }
            } catch (Throwable throwable) {
                // messages not attempted behind a failed forward are logged once, together, when they are failed
                if (!(throwable.getCause() instanceof NotForwardedException)) {
                    logFailedForward(binaryRequest, logCorrelationId, remoteAddress, throwable);
                }
                ctx.close();
            }
        }, false);
    }

    /**
     * @param handled run once the response has been written, or the failure logged and the client connection closed
     */
    private void processWaitingForResponse(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress, CompletableFuture<BinaryMessage> binaryResponseFuture, Runnable handled) {
        scheduler.submit(binaryResponseFuture, () -> {
            try {
                BinaryMessage binaryResponse = binaryResponseFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(FORWARDED_REQUEST)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("returning binary response:{}from:{}for forwarded binary request:{}")
                        .setArguments(SensitiveLogValue.of(formatBytes(binaryResponse.getBytes(), configuration.maxLoggedBodyBytes())), remoteAddress, SensitiveLogValue.of(formatBytes(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())))
                );
                if (binaryExchangeCallback != null) {
                    binaryExchangeCallback.onProxy(binaryRequest, binaryResponseFuture, remoteAddress, SocketAddresses.clientAddress(ctx.channel()));
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(binaryResponse.getBytes()));
            } catch (Throwable throwable) {
                logFailedForward(binaryRequest, logCorrelationId, remoteAddress, throwable);
                ctx.close();
            } finally {
                handled.run();
            }
        }, false);
    }

    /**
     * The one entry for a failed forward: the forward client leaves it to this caller, except a target refused by
     * forwardProxyBlockPrivateNetworks, which it logs as it refuses it. A forward refused for a header
     * limit was logged as a warning as it was refused, so the connection it closes is logged below that. A failure
     * of the upstream connection itself (it could not be made, failed, closed or timed out) is named without a stack
     * trace, as an HTTP forward's is; anything else keeps one.
     */
    private void logFailedForward(BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress, Throwable throwable) {
        if (throwable.getCause() instanceof ForwardTargetBlockedException) {
            // a refused target was logged where it was refused
            return;
        }
        HeaderLimitExceededException headerLimit = HeaderLimitExceededException.in(throwable);
        Level level = headerLimit != null ? Level.INFO : Level.WARN;
        if (mockServerLogger.isEnabledForInstance(level)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(level)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("exception{}sending hex{}to{}closing connection")
                    .setArguments(headerLimit != null ? headerLimit.getMessage() : boundedFaultMessage(throwable), SensitiveLogValue.of(hexDumpForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())), remoteAddress)
                    .setThrowable(headerLimit != null || upstreamConnectionFailure(throwable) ? null : boundedFault(throwable))
            );
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
        // a mid-pipeline handler that swallows channelReadComplete starves Netty's HTTP/2
        // flow-control flush (Http2ConnectionHandler.channelReadComplete -> writePendingBytes),
        // stalling any h2 response larger than the peer's initial window - so propagate the event
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        BinaryLocalReplies.discard(ctx.channel());
        BinaryRelay.clientInactive(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        BinaryRelay.clientWritabilityChanged(ctx.channel());
        ForwardQueue queue = ctx.channel().attr(FORWARD_QUEUE).get();
        if (queue != null) {
            // may run after writability has changed back, so it acts on what the channel says now
            if (ctx.channel().isWritable()) {
                resumeReadsOnceDrained(ctx, queue);
            } else {
                pauseReadsIfBehind(ctx, queue);
            }
        }
        ctx.fireChannelWritabilityChanged();
    }

    /**
     * A binary client that turns TLS on part way through reaches here once its server certificate is chosen, before
     * anything it sends over TLS is decrypted: the relay then starts TLS with the upstream on the same connection.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof SniCompletionEvent && ((SniCompletionEvent) event).isSuccess()) {
            BinaryRelay.clientStartedTls(ctx.channel());
        }
        super.userEventTriggered(ctx, event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (connectionClosedException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception caught by " + this.getClass() + " handler -> closing pipeline " + ctx.channel())
                    .setThrowable(cause)
            );
        } else if (isSslOrDecoderFault(cause)) {
            if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("SSL or decoder fault caught by " + this.getClass() + " handler -> closing pipeline " + ctx.channel() + sniDescription(ctx.channel()))
                        .setThrowable(boundedFault(cause))
                );
            }
        }
        closeOnFlush(ctx.channel());
    }
}
