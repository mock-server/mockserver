package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class HttpError extends Action<HttpError> {
    private int hashCode;
    private Boolean dropConnection;
    private Boolean resetConnection;
    private byte[] responseBytes;
    private Long streamError;

    /**
     * Well-known stream-level error codes for HTTP/2 (RFC 7540 section 7) and HTTP/3
     * (RFC 9114 section 8.1). The numeric {@link #code()} is the value written on the wire as the
     * RST_STREAM (HTTP/2) or RESET_STREAM (HTTP/3) error code. The HTTP/2 and HTTP/3 code spaces are
     * distinct; this enum exposes both families so an expectation can name a code by mnemonic. When a
     * code is supplied via {@link HttpError#withStreamError(long)} the raw numeric value is used
     * verbatim, so any future or vendor-specific code can still be injected.
     */
    public enum StreamErrorCode {
        // HTTP/2 error codes (RFC 7540 section 7)
        NO_ERROR(0x0L),
        PROTOCOL_ERROR(0x1L),
        INTERNAL_ERROR(0x2L),
        FLOW_CONTROL_ERROR(0x3L),
        SETTINGS_TIMEOUT(0x4L),
        STREAM_CLOSED(0x5L),
        FRAME_SIZE_ERROR(0x6L),
        REFUSED_STREAM(0x7L),
        CANCEL(0x8L),
        COMPRESSION_ERROR(0x9L),
        CONNECT_ERROR(0xaL),
        ENHANCE_YOUR_CALM(0xbL),
        INADEQUATE_SECURITY(0xcL),
        HTTP_1_1_REQUIRED(0xdL),
        // HTTP/3 error codes (RFC 9114 section 8.1)
        H3_NO_ERROR(0x100L),
        H3_GENERAL_PROTOCOL_ERROR(0x101L),
        H3_INTERNAL_ERROR(0x102L),
        H3_STREAM_CREATION_ERROR(0x103L),
        H3_CLOSED_CRITICAL_STREAM(0x104L),
        H3_FRAME_UNEXPECTED(0x105L),
        H3_FRAME_ERROR(0x106L),
        H3_EXCESSIVE_LOAD(0x107L),
        H3_ID_ERROR(0x108L),
        H3_SETTINGS_ERROR(0x109L),
        H3_MISSING_SETTINGS(0x10aL),
        H3_REQUEST_REJECTED(0x10bL),
        H3_REQUEST_CANCELLED(0x10cL),
        H3_REQUEST_INCOMPLETE(0x10dL),
        H3_MESSAGE_ERROR(0x10eL),
        H3_CONNECT_ERROR(0x10fL),
        H3_VERSION_FALLBACK(0x110L);

        private final long code;

        StreamErrorCode(long code) {
            this.code = code;
        }

        public long code() {
            return code;
        }

        /**
         * Resolve a well-known code by mnemonic name (case-insensitive), returning null when the
         * name is not recognised so the caller can decide how to handle an unknown name.
         */
        public static StreamErrorCode byName(String name) {
            if (name == null) {
                return null;
            }
            for (StreamErrorCode value : values()) {
                if (value.name().equalsIgnoreCase(name.trim())) {
                    return value;
                }
            }
            return null;
        }
    }

    public static HttpError error() {
        return new HttpError();
    }

    /**
     * Forces the connection to be dropped without any response being returned.
     * <p>
     * Precedence: when {@link #withResetConnection(Boolean) resetConnection} is set it takes precedence. When
     * {@link #withStreamError(long) streamError} is also set it takes precedence and
     * {@code dropConnection} is ignored (the matched request stream is reset instead of the whole
     * connection being dropped). Note that on HTTP/1.1 a streamError has no stream to reset and itself
     * falls back to dropping the connection, so the observable behaviour is the same there.
     *
     * @param dropConnection if true the connection is drop without any response being returned
     */
    public HttpError withDropConnection(Boolean dropConnection) {
        this.dropConnection = dropConnection;
        this.hashCode = 0;
        return this;
    }

    public Boolean getDropConnection() {
        return dropConnection;
    }

    /**
     * Resets the connection with a TCP RST (the socket is closed with {@code SO_LINGER} 0) instead of
     * returning a response, so the client sees "connection reset by peer" ({@code ECONNRESET}) rather than
     * the clean close of {@link #withDropConnection(Boolean) dropConnection}. Over TLS the reset is sent
     * without a TLS {@code close_notify}. Over HTTP/2 the whole TCP connection is reset, failing every
     * stream on it (use {@link #withStreamError(long) streamError} to reset one stream). Over HTTP/3, which
     * runs on UDP and has no TCP reset, the QUIC connection is closed at once with {@code H3_INTERNAL_ERROR}.
     * <p>
     * Precedence: takes precedence over {@code streamError} and {@code dropConnection}. Any
     * {@link #withResponseBytes(byte[]) responseBytes} are written first, but a reset discards whatever the
     * client has not yet received, so they may never arrive.
     *
     * @param resetConnection if true the connection is reset without any response being returned
     */
    public HttpError withResetConnection(Boolean resetConnection) {
        this.resetConnection = resetConnection;
        this.hashCode = 0;
        return this;
    }

    public Boolean getResetConnection() {
        return resetConnection;
    }

    /**
     * The raw response to be returned, allowing the expectation to specify any invalid response as a raw byte[]
     *
     * @param responseBytes the exact bytes that will be returned
     */
    public HttpError withResponseBytes(byte[] responseBytes) {
        this.responseBytes = responseBytes;
        this.hashCode = 0;
        return this;
    }

    public byte[] getResponseBytes() {
        return responseBytes;
    }

    /**
     * Resets the individual request stream with the supplied error code instead of returning a normal
     * response. Over HTTP/2 this sends a RST_STREAM frame for the matched stream (RFC 7540 section 6.4);
     * over HTTP/3 this sends a RESET_STREAM for the matched QUIC stream (RFC 9114 section 4.1). Other
     * multiplexed streams on the same connection are unaffected. HTTP/1.1 has no stream concept, so a
     * stream error falls back to dropping the whole connection.
     *
     * @param streamError the stream/RST error code to send (32-bit for HTTP/2 per RFC 7540 section 7;
     *                     HTTP/3 codes per RFC 9114 section 8.1)
     */
    public HttpError withStreamError(long streamError) {
        this.streamError = streamError;
        this.hashCode = 0;
        return this;
    }

    /**
     * Resets the individual request stream with a well-known error code. Convenience overload of
     * {@link #withStreamError(long)} that takes a {@link StreamErrorCode} mnemonic.
     *
     * @param streamErrorCode the well-known error code to send
     */
    public HttpError withStreamError(StreamErrorCode streamErrorCode) {
        return withStreamError(streamErrorCode.code());
    }

    /**
     * Resets the individual request stream with a well-known error code named by its mnemonic, e.g.
     * {@code "REFUSED_STREAM"} or {@code "H3_REQUEST_CANCELLED"} (case-insensitive). Convenience
     * overload of {@link #withStreamError(long)}.
     *
     * @param streamErrorCodeName the mnemonic of a {@link StreamErrorCode}
     * @throws IllegalArgumentException if the name is not a known stream error code
     */
    public HttpError withStreamErrorCodeName(String streamErrorCodeName) {
        StreamErrorCode code = StreamErrorCode.byName(streamErrorCodeName);
        if (code == null) {
            throw new IllegalArgumentException("unknown stream error code name \"" + streamErrorCodeName + "\", expected one of " + Arrays.toString(StreamErrorCode.values()).toLowerCase(Locale.ROOT));
        }
        return withStreamError(code);
    }

    /**
     * The stream/RST error code to send, or null when no stream error is configured (the default,
     * preserving the existing dropConnection/responseBytes behaviour).
     */
    public Long getStreamError() {
        return streamError;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.ERROR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpError httpError = (HttpError) o;
        return Objects.equals(dropConnection, httpError.dropConnection) &&
            Objects.equals(resetConnection, httpError.resetConnection) &&
            Objects.equals(streamError, httpError.streamError) &&
            Arrays.equals(responseBytes, httpError.responseBytes);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = Objects.hash(super.hashCode(), dropConnection, resetConnection, streamError);
            hashCode = 31 * result + Arrays.hashCode(responseBytes);
        }
        return hashCode;
    }
}
