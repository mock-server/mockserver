package org.mockserver.mock.breakpoint;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.BreakpointReleasedDTO;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;
import org.mockserver.uuid.UUIDService;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;

import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;

/**
 * Dispatches stream-frame breakpoint-held frames to a callback WebSocket client for
 * interactive resolution. This is the per-frame analogue of
 * {@link BreakpointCallbackDispatcher} (which handles buffered REQUEST/RESPONSE phases).
 *
 * <h3>Protocol (frozen contract)</h3>
 * <ul>
 *     <li><b>Server to client:</b> a {@link PausedStreamFrameDTO} carrying the
 *         correlationId, streamId, sequenceNumber, direction, phase, body (Base64),
 *         and light request context.</li>
 *     <li><b>Client to server:</b> a {@link StreamFrameDecisionDTO} carrying the
 *         correlationId, action (CONTINUE/MODIFY/DROP/INJECT/CLOSE), and optional
 *         body (Base64, for MODIFY/INJECT).</li>
 * </ul>
 *
 * <h3>Safety rails</h3>
 * <ul>
 *     <li>Timeout: auto-continues after {@link Configuration#breakpointTimeoutMillis()}.</li>
 *     <li>Max-held cap: shared with {@link BreakpointCallbackDispatcher}.</li>
 *     <li>Disconnect: all in-flight dispatches for a disconnected client are
 *         auto-completed to CONTINUE via {@link #autoCompleteForClient(String)}.</li>
 * </ul>
 *
 * <h3>Frame ordering</h3>
 * <p>Frames within a stream are dispatched one at a time: the caller parks the frame
 * and chains its continuation on the returned future. The existing backpressure
 * mechanisms (streaming body chunkWritten(bytes), autoRead=false, withhold ctx.read()) ensure
 * that the next frame is not delivered until the current one resolves — preserving the
 * same ordering guarantees as the REST-park path.
 *
 * <p>This class is thread-safe. Correlation handlers are registered before the WS
 * message is sent, and cleaned up on completion/timeout/disconnect.
 */
public class StreamFrameCallbackDispatcher {

    private static final StreamFrameCallbackDispatcher INSTANCE = new StreamFrameCallbackDispatcher();

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MockServer-stream-frame-ws-timeout");
        t.setDaemon(true);
        return t;
    });

    /**
     * Tracks in-flight dispatch futures keyed by correlationId so they can be
     * auto-completed on client disconnect.
     */
    private final ConcurrentHashMap<String, InFlightStreamDispatch> inFlight = new ConcurrentHashMap<>();

    public static StreamFrameCallbackDispatcher getInstance() {
        return INSTANCE;
    }

    /**
     * Dispatches a stream frame to a callback WebSocket client for interactive resolution,
     * tagging the message with the matched breakpoint id and request timestamp.
     *
     * @param clientId               the owning callback client
     * @param breakpointId           the matched breakpoint's id (may be null)
     * @param streamId               the stream this frame belongs to
     * @param sequenceNumber         0-based sequence number within the stream
     * @param direction              INBOUND or OUTBOUND
     * @param phase                  RESPONSE_STREAM or INBOUND_STREAM
     * @param capturedBytes          the frame payload bytes (already copied from ByteBuf)
     * @param requestMethod          HTTP method of the original request (nullable)
     * @param requestPath            path of the original request (nullable)
     * @param requestTimestamp       epoch-millis when MockServer first received the request (nullable)
     * @param webSocketClientRegistry the WS registry for sending messages
     * @param configuration          for timeout and max-held
     * @param logger                 for logging
     * @return a future that completes with the stream frame decision, or {@code null}
     *         if the max-held cap is reached or the client is not connected
     */
    public CompletableFuture<StreamFrameDecision> dispatchFrame(
        String clientId,
        String breakpointId,
        String streamId,
        int sequenceNumber,
        PausedStreamFrame.Direction direction,
        BreakpointPhase phase,
        byte[] capturedBytes,
        String requestMethod,
        String requestPath,
        Long requestTimestamp,
        WebSocketClientRegistry webSocketClientRegistry,
        Configuration configuration,
        MockServerLogger logger
    ) {
        // max-held cap (shared with all breakpoint registries and dispatchers)
        int maxHeld = configuration.breakpointMaxHeld();
        int totalHeld = BreakpointCallbackDispatcher.totalHeldCount();
        if (totalHeld >= maxHeld) {
            if (logger != null && logger.isEnabledForInstance(INFO)) {
                logger.logEvent(new LogEntry().setLogLevel(INFO)
                    .setMessageFormat("stream frame WS dispatch cap reached ({}/{}), skipping for stream={} seq={}")
                    .setArguments(totalHeld, maxHeld, streamId, sequenceNumber));
            }
            return null;
        }

        String correlationId = UUIDService.getUUID();
        CompletableFuture<StreamFrameDecision> future = new CompletableFuture<>();
        InFlightStreamDispatch dispatch = new InFlightStreamDispatch(correlationId, clientId, streamId, webSocketClientRegistry, future);
        inFlight.put(correlationId, dispatch);

        // Register the stream-frame decision callback handler
        webSocketClientRegistry.registerStreamFrameCallbackHandler(correlationId, decisionDTO -> {
            StreamFrameDecision decision = mapDecision(decisionDTO);
            future.complete(decision);
        });

        // Schedule timeout auto-continue
        long timeoutMillis = configuration.breakpointTimeoutMillis();
        ScheduledFuture<?> timeoutHandle = TIMEOUT_SCHEDULER.schedule(() -> {
            if (future.complete(StreamFrameDecision.continueFrame())) {
                notifyReleased(dispatch, BreakpointReleasedDTO.REASON_TIMEOUT,
                    "The paused frame was not resolved within the breakpoint timeout (" + timeoutMillis + " ms), so MockServer continued it unchanged.");
                if (logger != null && logger.isEnabledForInstance(INFO)) {
                    logger.logEvent(new LogEntry().setLogLevel(INFO)
                        .setMessageFormat("stream frame WS dispatch auto-continued (timeout {}ms) for stream={} seq={}")
                        .setArguments(timeoutMillis, streamId, sequenceNumber));
                }
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);

        // Single cleanup point for all completion routes.
        // Use conditional remove so a stale callback cannot remove a newer dispatch.
        future.whenComplete((decision, throwable) -> {
            timeoutHandle.cancel(false);
            webSocketClientRegistry.unregisterStreamFrameCallbackHandler(correlationId);
            inFlight.remove(correlationId, dispatch);
        });

        // Build the paused-frame DTO
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId(correlationId)
            .setStreamId(streamId)
            .setSequenceNumber(sequenceNumber)
            .setDirection(direction.name())
            .setPhase(phase.name())
            .setBody(Base64.getEncoder().encodeToString(capturedBytes))
            .setRequestMethod(requestMethod)
            .setRequestPath(requestPath)
            .setBreakpointId(breakpointId)
            .setRequestTimestamp(requestTimestamp);

        // Send to the client
        if (!webSocketClientRegistry.sendStreamFrameMessage(clientId, dto)) {
            // Client not connected — auto-continue (triggers whenComplete cleanup)
            future.complete(StreamFrameDecision.continueFrame());
            if (logger != null && logger.isEnabledForInstance(WARN)) {
                logger.logEvent(new LogEntry().setLogLevel(WARN)
                    .setMessageFormat("stream frame WS dispatch failed: client {} not connected for stream={} seq={}")
                    .setArguments(clientId, streamId, sequenceNumber));
            }
            return null;
        }

        return future;
    }

    /**
     * Auto-completes all in-flight WS stream frame dispatches for the given client
     * with CONTINUE. Called when the client's WebSocket connection closes.
     *
     * @param clientId the disconnecting client
     * @return the number of in-flight dispatches auto-completed
     */
    public int autoCompleteForClient(String clientId) {
        if (clientId == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, InFlightStreamDispatch> entry : inFlight.entrySet()) {
            InFlightStreamDispatch dispatch = entry.getValue();
            if (clientId.equals(dispatch.clientId) && !dispatch.future.isDone()) {
                dispatch.future.complete(StreamFrameDecision.continueFrame());
                count++;
            }
        }
        return count;
    }

    /**
     * Drops every frame of the given stream that is still waiting for a decision, telling
     * each owning client that the frame is no longer paused. Called when a stream ends
     * without its held frames being deliverable (the client went away or the source failed).
     *
     * @param streamId the stream that ended
     * @return the number of held frames dropped
     */
    public int releaseStream(String streamId) {
        if (streamId == null) {
            return 0;
        }
        int count = 0;
        for (InFlightStreamDispatch dispatch : inFlight.values()) {
            if (streamId.equals(dispatch.streamId) && dispatch.future.complete(StreamFrameDecision.drop())) {
                notifyReleased(dispatch, BreakpointReleasedDTO.REASON_STREAM_ENDED,
                    "The stream ended before the paused frame could be delivered, so MockServer dropped it.");
                count++;
            }
        }
        return count;
    }

    private static void notifyReleased(InFlightStreamDispatch dispatch, String reason, String message) {
        dispatch.webSocketClientRegistry.sendBreakpointReleased(dispatch.clientId, new BreakpointReleasedDTO()
            .setCorrelationId(dispatch.correlationId)
            .setReason(reason)
            .setMessage(message));
    }

    /**
     * Returns the number of in-flight WS stream frame dispatches.
     */
    public int inFlightCount() {
        return inFlight.size();
    }

    /**
     * Resets all in-flight dispatches (auto-continue) — called on server reset.
     *
     * <p>Takes a snapshot and clears the map BEFORE completing futures, so that
     * asynchronous {@code whenComplete} callbacks cannot race with subsequent
     * dispatches that re-populate the map.
     */
    public void reset() {
        java.util.List<InFlightStreamDispatch> snapshot = new java.util.ArrayList<>(inFlight.values());
        inFlight.clear();
        for (InFlightStreamDispatch dispatch : snapshot) {
            dispatch.future.complete(StreamFrameDecision.continueFrame());
        }
    }

    /**
     * Convenience method that checks whether the given matched breakpoint should use
     * WS-callback dispatch (non-null clientId + registry available), and if so, dispatches
     * the frame. If WS dispatch is not applicable, returns {@code null} so the caller can
     * fall through to the REST-park path.
     *
     * <p>This consolidates the "should I use WS dispatch?" branching logic in one place
     * so each hold point doesn't need to repeat the clientId/registry checks.
     *
     * @param matchedBreakpoint the matched breakpoint (must not be null)
     * @param streamId          the stream this frame belongs to
     * @param sequenceNumber    0-based sequence number within the stream
     * @param direction         INBOUND or OUTBOUND
     * @param phase             RESPONSE_STREAM or INBOUND_STREAM
     * @param capturedBytes     the frame payload bytes
     * @param requestMethod     HTTP method of the original request
     * @param requestPath       path of the original request
     * @param configuration     for timeout and max-held
     * @param logger            for logging
     * @param webSocketClientRegistry the WS registry for sending messages (may be null)
     * @return a future that completes with the decision if WS dispatch was used;
     *         {@code null} if WS dispatch is not applicable or the dispatch was rejected
     *         (cap/client-not-connected — caller should write immediately)
     */
    public CompletableFuture<StreamFrameDecision> tryWsDispatch(
        BreakpointMatcher matchedBreakpoint,
        String streamId,
        int sequenceNumber,
        PausedStreamFrame.Direction direction,
        BreakpointPhase phase,
        byte[] capturedBytes,
        String requestMethod,
        String requestPath,
        Configuration configuration,
        MockServerLogger logger,
        WebSocketClientRegistry webSocketClientRegistry
    ) {
        return tryWsDispatch(matchedBreakpoint, streamId, sequenceNumber, direction, phase,
            capturedBytes, requestMethod, requestPath, null, configuration, logger, webSocketClientRegistry);
    }

    /**
     * Overload of {@link #tryWsDispatch} that includes the request timestamp.
     */
    public CompletableFuture<StreamFrameDecision> tryWsDispatch(
        BreakpointMatcher matchedBreakpoint,
        String streamId,
        int sequenceNumber,
        PausedStreamFrame.Direction direction,
        BreakpointPhase phase,
        byte[] capturedBytes,
        String requestMethod,
        String requestPath,
        Long requestTimestamp,
        Configuration configuration,
        MockServerLogger logger,
        WebSocketClientRegistry webSocketClientRegistry
    ) {
        String clientId = matchedBreakpoint.getClientId();
        if (clientId == null || webSocketClientRegistry == null) {
            return null; // not applicable — caller uses REST-park
        }
        return dispatchFrame(clientId, matchedBreakpoint.getId(), streamId, sequenceNumber, direction, phase,
            capturedBytes, requestMethod, requestPath, requestTimestamp, webSocketClientRegistry, configuration, logger);
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StreamFrameCallbackDispatcher.class);

    /**
     * Maps a {@link StreamFrameDecisionDTO} to a {@link StreamFrameDecision}.
     * Unknown or null actions default to CONTINUE. MODIFY/INJECT with a null body
     * safely degrades to CONTINUE but logs a WARN so client bugs are visible.
     */
    static StreamFrameDecision mapDecision(StreamFrameDecisionDTO dto) {
        if (dto == null || dto.getAction() == null) {
            return StreamFrameDecision.continueFrame();
        }
        try {
            StreamFrameDecision.Action action = StreamFrameDecision.Action.valueOf(dto.getAction());
            switch (action) {
                case CONTINUE:
                    return StreamFrameDecision.continueFrame();
                case MODIFY: {
                    if (dto.getBody() == null) {
                        LOG.warn("stream frame decision {} has null body, degrading to CONTINUE (correlationId={})",
                            action, dto.getCorrelationId());
                        return StreamFrameDecision.continueFrame();
                    }
                    byte[] body = Base64.getDecoder().decode(dto.getBody());
                    return StreamFrameDecision.modify(body);
                }
                case DROP:
                    return StreamFrameDecision.drop();
                case INJECT: {
                    if (dto.getBody() == null) {
                        LOG.warn("stream frame decision {} has null body, degrading to CONTINUE (correlationId={})",
                            action, dto.getCorrelationId());
                        return StreamFrameDecision.continueFrame();
                    }
                    byte[] body = Base64.getDecoder().decode(dto.getBody());
                    return StreamFrameDecision.inject(body);
                }
                case CLOSE:
                    return StreamFrameDecision.close();
                default:
                    return StreamFrameDecision.continueFrame();
            }
        } catch (IllegalArgumentException e) {
            // Unknown action string — default to continue
            return StreamFrameDecision.continueFrame();
        }
    }

    /**
     * Tracks an in-flight WS stream frame dispatch for disconnect cleanup.
     */
    private static class InFlightStreamDispatch {
        final String correlationId;
        final String clientId;
        final String streamId;
        final WebSocketClientRegistry webSocketClientRegistry;
        final CompletableFuture<StreamFrameDecision> future;

        InFlightStreamDispatch(String correlationId, String clientId, String streamId, WebSocketClientRegistry webSocketClientRegistry, CompletableFuture<StreamFrameDecision> future) {
            this.correlationId = correlationId;
            this.clientId = clientId;
            this.streamId = streamId;
            this.webSocketClientRegistry = webSocketClientRegistry;
            this.future = future;
        }
    }
}
