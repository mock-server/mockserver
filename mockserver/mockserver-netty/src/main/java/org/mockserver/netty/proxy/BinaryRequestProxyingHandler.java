package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.util.AttributeKey;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
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
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.mockserver.exception.ExceptionHandling.boundedFault;
import static org.mockserver.exception.ExceptionHandling.closeOnFlush;
import static org.mockserver.exception.ExceptionHandling.connectionClosedException;
import static org.mockserver.exception.ExceptionHandling.isSslOrDecoderFault;
import static org.mockserver.exception.ExceptionHandling.sniDescription;
import static org.mockserver.formatting.StringFormatter.formatBytes;
import static org.mockserver.formatting.StringFormatter.hexDumpForLog;
import static org.mockserver.formatting.StringFormatter.utf8ForLog;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static org.mockserver.model.BinaryMessage.bytes;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class BinaryRequestProxyingHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final AttributeKey<Boolean> UNTRACKED_REPLIES_WARNED = AttributeKey.valueOf("BINARY_UNTRACKED_REPLIES_WARNED");

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
        // logged once matched, so it names the expectation that answered it for verification by expectation id
        LogEntry receivedRequest = new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setCorrelationId(logCorrelationId)
            .setHttpRequest(binaryRequestDefinition(binaryRequest, logCorrelationId))
            .setMessageFormat("received binary request:{}")
            .setArguments(SensitiveLogValue.of(hexDumpForLog(binaryRequest.getBytes(), configuration.maxLoggedBodyBytes())));
        final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
        if (remoteAddress != null) {
            Expectation matchedExpectation = matchThenLogReceived(receivedRequest, () -> relayedMessageExpectation(ctx, binaryRequest, logCorrelationId, remoteAddress));
            if (matchedExpectation == null || !answeredByExpectation(ctx, matchedExpectation, binaryRequest, logCorrelationId, remoteAddress)) {
                BinaryRelay.forward(ctx, binaryRequest, logCorrelationId, UpstreamReply.RELAY, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
            }
        } else if (httpState != null) {
            Expectation matchedExpectation = matchThenLogReceived(receivedRequest, () -> firstMatchingBinaryExpectation(binaryRequest, logCorrelationId));
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
            mockServerLogger.logEvent(receivedRequest);
            writeUnknownFormatMessage(ctx, binaryRequest, logCorrelationId);
        }
    }

    /**
     * Logs the received-request entry at every level, as an HTTP request is, so verify and retrieve can find it, with
     * the id of the expectation that answers it: one whose binary response is written or replaces the upstream's. It
     * is logged even if matching fails.
     */
    private Expectation matchThenLogReceived(LogEntry receivedRequest, Supplier<Expectation> match) {
        Expectation matchedExpectation = null;
        try {
            matchedExpectation = match.get();
            return matchedExpectation;
        } finally {
            boolean answered = matchedExpectation != null && matchedExpectation.getBinaryResponse() != null;
            mockServerLogger.logEvent(receivedRequest.setExpectationId(answered ? matchedExpectation.getId() : null));
        }
    }

    private Expectation firstMatchingBinaryExpectation(BinaryMessage binaryRequest, String logCorrelationId) {
        return httpState.firstMatchingExpectation(binaryRequestDefinition(binaryRequest, logCorrelationId));
    }

    private static BinaryRequestDefinition binaryRequestDefinition(BinaryMessage binaryRequest, String logCorrelationId) {
        BinaryRequestDefinition binaryRequestDefinition = BinaryRequestDefinition.binaryRequest(binaryRequest.getBytes());
        binaryRequestDefinition.withLogCorrelationId(logCorrelationId);
        return binaryRequestDefinition;
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
     * With matchBinaryExpectationsWhenProxying, a message on a proxied connection whose bytes match a binary
     * expectation is answered here and not forwarded, or, as the binary response's {@code upstream} says, answered
     * and forwarded or forwarded with its reply replaced. A message no expectation can match is forwarded without a
     * matcher pass, so it adds no match entries to the log.
     *
     * @return the expectation the message matched, or null for none or a connection whose messages are not matched
     */
    private Expectation relayedMessageExpectation(ChannelHandlerContext ctx, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
        if (httpState == null || !configuration.matchBinaryExpectationsWhenProxying()) {
            return null;
        }
        // made without connecting, so a connection whose messages are all answered opens no upstream connection
        BinaryRelay.prepare(ctx, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
        if (!httpState.mayMatchBinaryMessage(binaryRequest.getBytes())) {
            return null;
        }
        return firstMatchingBinaryExpectation(binaryRequest, logCorrelationId);
    }

    /**
     * Answers a relayed message that matched an expectation, as {@link #relayedMessageExpectation} describes.
     *
     * @return whether the message was handled here, so is not to be forwarded by the caller
     */
    private boolean answeredByExpectation(ChannelHandlerContext ctx, Expectation matchedExpectation, BinaryMessage binaryRequest, String logCorrelationId, InetSocketAddress remoteAddress) {
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
     * @return false, having opened nothing, for a connection with no target
     */
    public boolean connectBeforeClientSpeaks(ChannelHandlerContext ctx) {
        InetSocketAddress remoteAddress = getRemoteAddress(ctx);
        if (remoteAddress == null) {
            return false;
        }
        BinaryRelay.openBeforeClientSpeaks(ctx, remoteAddress, configuration, mockServerLogger, scheduler, httpClient, binaryExchangeCallback);
        return true;
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

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        // nothing read from now on reaches this handler, so it has no reason left to hold reads back
        BinaryLocalReplies.discard(ctx.channel());
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
        BinaryLocalReplies.writabilityChanged(ctx.channel());
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
