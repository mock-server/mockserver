package org.mockserver.responsewriter;

/**
 * Transport-neutral seam for resetting the request stream with an error code instead of returning a
 * response (an {@link org.mockserver.model.HttpError} carrying {@code streamError}).
 * <p>
 * HTTP/2 stream resets (RST_STREAM) are issued directly by
 * {@link org.mockserver.mock.action.http.HttpErrorActionHandler} on the Netty channel, because the
 * HTTP/2 frame/connection handlers are on the core classpath. HTTP/3 resets a QUIC stream
 * (RESET_STREAM) and the QUIC types live only in the netty module, so
 * {@link org.mockserver.mock.action.http.HttpActionHandler} delegates to this interface when the
 * active {@link ResponseWriter} implements it (the HTTP/3 response writer does). This keeps the
 * matching/dispatch logic in core transport-agnostic while the actual QUIC stream reset lives in the
 * netty module.
 */
public interface StreamErrorWriter {

    /**
     * Reset the current request stream with the given error code (HTTP/3 RESET_STREAM,
     * RFC 9114 section 4.1 / section 8.1).
     *
     * @param errorCode the application/stream error code to send on the reset
     */
    void writeStreamError(long errorCode);

    /**
     * Abort the whole connection, the transport's nearest equivalent of a TCP reset (an
     * {@link org.mockserver.model.HttpError} carrying {@code resetConnection}). HTTP/3 runs on UDP, which
     * has no reset, so the QUIC connection is closed at once with an error code.
     */
    void writeConnectionReset();
}
