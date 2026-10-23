package org.mockserver.model;

import java.util.Objects;

/**
 * Declarative TCP-layer fault/chaos injection profile, applied at the raw byte
 * level <em>before</em> HTTP decoding. Each fault type mirrors one of
 * Toxiproxy's named toxics:
 * <ul>
 *   <li><b>latency</b> — delay all inbound data by {@code latencyMs} milliseconds</li>
 *   <li><b>down</b> — silently drop all inbound data (service appears down)</li>
 *   <li><b>bandwidth</b> — throttle inbound data to {@code bandwidthBytesPerSec}</li>
 *   <li><b>slow_close</b> — delay the TCP FIN by 2 seconds</li>
 *   <li><b>timeout</b> — never send FIN; connection hangs on close</li>
 *   <li><b>reset_peer</b> — send TCP RST and close immediately</li>
 *   <li><b>slicer</b> — fragment inbound data into chunks of {@code slicerChunkSize} bytes</li>
 *   <li><b>limit_data</b> — close the connection after {@code limitDataBytes} bytes received</li>
 * </ul>
 * <p>
 * Profiles are registered in the {@link org.mockserver.mock.action.http.TcpChaosRegistry}
 * and applied by the {@code TcpChaosHandler} Netty handler. When multiple faults are
 * configured on the same profile, they are evaluated in priority order: down, resetPeer,
 * limitData, slicer, bandwidth, latency.
 * <p>
 * Follows the model field/{@code withX}/getter convention so it round-trips through
 * Jackson without a bespoke (de)serializer.
 */
public class TcpChaosProfile extends ObjectWithJsonToString {

    private int hashCode;
    private Long latencyMs;
    private Boolean down;
    private Long bandwidthBytesPerSec;
    private Boolean slowClose;
    private Boolean timeout;
    private Boolean resetPeer;
    private Integer slicerChunkSize;
    private Long limitDataBytes;
    // --- Connection-lifecycle response-path faults (v1) ---
    // These fire at response/dispatch time (not connect time): the client sees the fault while or
    // after the response head is written, mimicking a server that crashes or is preempted mid-exchange
    // rather than one that refuses the connection up front.
    private Boolean resetMidResponse;
    private Integer resetAfterResponseChunks;
    private Delay slowCloseDelay;
    private Boolean http2GoAway;
    private Long http2GoAwayErrorCode;
    private Long http2GoAwayLastStreamId;

    public static TcpChaosProfile tcpChaosProfile() {
        return new TcpChaosProfile();
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public TcpChaosProfile withLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
        this.hashCode = 0;
        return this;
    }

    public Boolean getDown() {
        return down;
    }

    public TcpChaosProfile withDown(Boolean down) {
        this.down = down;
        this.hashCode = 0;
        return this;
    }

    public Long getBandwidthBytesPerSec() {
        return bandwidthBytesPerSec;
    }

    public TcpChaosProfile withBandwidthBytesPerSec(Long bandwidthBytesPerSec) {
        if (bandwidthBytesPerSec != null && bandwidthBytesPerSec < 1) {
            throw new IllegalArgumentException("bandwidthBytesPerSec must be >= 1, got " + bandwidthBytesPerSec);
        }
        this.bandwidthBytesPerSec = bandwidthBytesPerSec;
        this.hashCode = 0;
        return this;
    }

    public Boolean getSlowClose() {
        return slowClose;
    }

    public TcpChaosProfile withSlowClose(Boolean slowClose) {
        this.slowClose = slowClose;
        this.hashCode = 0;
        return this;
    }

    public Boolean getTimeout() {
        return timeout;
    }

    public TcpChaosProfile withTimeout(Boolean timeout) {
        this.timeout = timeout;
        this.hashCode = 0;
        return this;
    }

    public Boolean getResetPeer() {
        return resetPeer;
    }

    public TcpChaosProfile withResetPeer(Boolean resetPeer) {
        this.resetPeer = resetPeer;
        this.hashCode = 0;
        return this;
    }

    public Integer getSlicerChunkSize() {
        return slicerChunkSize;
    }

    public TcpChaosProfile withSlicerChunkSize(Integer slicerChunkSize) {
        if (slicerChunkSize != null && slicerChunkSize < 1) {
            throw new IllegalArgumentException("slicerChunkSize must be >= 1, got " + slicerChunkSize);
        }
        this.slicerChunkSize = slicerChunkSize;
        this.hashCode = 0;
        return this;
    }

    public Long getLimitDataBytes() {
        return limitDataBytes;
    }

    public TcpChaosProfile withLimitDataBytes(Long limitDataBytes) {
        if (limitDataBytes != null && limitDataBytes < 1) {
            throw new IllegalArgumentException("limitDataBytes must be >= 1, got " + limitDataBytes);
        }
        this.limitDataBytes = limitDataBytes;
        this.hashCode = 0;
        return this;
    }

    public Boolean getResetMidResponse() {
        return resetMidResponse;
    }

    /**
     * When true, once the response has been written the socket is forced to RST (SO_LINGER 0 +
     * forced close) instead of a clean FIN, so a client reading on past the response sees the
     * connection reset — the "server crashed while replying" fault. Applied in the response writer,
     * not at connect time.
     */
    public TcpChaosProfile withResetMidResponse(Boolean resetMidResponse) {
        this.resetMidResponse = resetMidResponse;
        this.hashCode = 0;
        return this;
    }

    public Integer getResetAfterResponseChunks() {
        return resetAfterResponseChunks;
    }

    /**
     * Number of response body chunks to write before the mid-response RST. {@code null} (or 0) means
     * "reset after the head". v1 only honours the "after head" semantics precisely; values &gt; 0 for
     * non-chunked bodies are deferred and treated as "after head".
     */
    public TcpChaosProfile withResetAfterResponseChunks(Integer resetAfterResponseChunks) {
        if (resetAfterResponseChunks != null && resetAfterResponseChunks < 0) {
            throw new IllegalArgumentException("resetAfterResponseChunks must be >= 0, got " + resetAfterResponseChunks);
        }
        this.resetAfterResponseChunks = resetAfterResponseChunks;
        this.hashCode = 0;
        return this;
    }

    public Delay getSlowCloseDelay() {
        return slowCloseDelay;
    }

    /**
     * Host-scoped (optionally jittered) delay applied before the socket FIN on the response path,
     * even when {@code ConnectionOptions.closeSocketDelay} is null. Lets a host be made to linger on
     * close without a per-expectation connection option.
     */
    public TcpChaosProfile withSlowCloseDelay(Delay slowCloseDelay) {
        this.slowCloseDelay = slowCloseDelay;
        this.hashCode = 0;
        return this;
    }

    public Boolean getHttp2GoAway() {
        return http2GoAway;
    }

    /**
     * When true and the connection is HTTP/2, a GOAWAY frame is emitted on the response path so the
     * client stops opening new streams and drains — the graceful "this connection is going away"
     * signal. HTTP/1.1 has no GOAWAY and degrades to {@code Connection: close} + 503 elsewhere.
     */
    public TcpChaosProfile withHttp2GoAway(Boolean http2GoAway) {
        this.http2GoAway = http2GoAway;
        this.hashCode = 0;
        return this;
    }

    public Long getHttp2GoAwayErrorCode() {
        return http2GoAwayErrorCode;
    }

    /**
     * HTTP/2 error code carried on the GOAWAY frame. {@code null} defaults to 0 (NO_ERROR), the
     * graceful-shutdown code.
     */
    public TcpChaosProfile withHttp2GoAwayErrorCode(Long http2GoAwayErrorCode) {
        if (http2GoAwayErrorCode != null && http2GoAwayErrorCode < 0) {
            throw new IllegalArgumentException("http2GoAwayErrorCode must be >= 0, got " + http2GoAwayErrorCode);
        }
        this.http2GoAwayErrorCode = http2GoAwayErrorCode;
        this.hashCode = 0;
        return this;
    }

    public Long getHttp2GoAwayLastStreamId() {
        return http2GoAwayLastStreamId;
    }

    /**
     * The {@code lastStreamId} carried on the GOAWAY frame. {@code null} means "use the current /
     * last-processed stream id" (resolved at emit time by the connection handler).
     */
    public TcpChaosProfile withHttp2GoAwayLastStreamId(Long http2GoAwayLastStreamId) {
        if (http2GoAwayLastStreamId != null && http2GoAwayLastStreamId < 0) {
            throw new IllegalArgumentException("http2GoAwayLastStreamId must be >= 0, got " + http2GoAwayLastStreamId);
        }
        this.http2GoAwayLastStreamId = http2GoAwayLastStreamId;
        this.hashCode = 0;
        return this;
    }

    /**
     * Returns {@code true} when at least one fault is configured (non-null and meaningful).
     */
    public boolean hasAnyFault() {
        return (latencyMs != null && latencyMs > 0)
            || Boolean.TRUE.equals(down)
            || (bandwidthBytesPerSec != null && bandwidthBytesPerSec > 0)
            || Boolean.TRUE.equals(slowClose)
            || Boolean.TRUE.equals(timeout)
            || Boolean.TRUE.equals(resetPeer)
            || (slicerChunkSize != null && slicerChunkSize > 0)
            || (limitDataBytes != null && limitDataBytes > 0)
            || Boolean.TRUE.equals(resetMidResponse)
            || (slowCloseDelay != null)
            || Boolean.TRUE.equals(http2GoAway);
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
        TcpChaosProfile that = (TcpChaosProfile) o;
        return Objects.equals(latencyMs, that.latencyMs)
            && Objects.equals(down, that.down)
            && Objects.equals(bandwidthBytesPerSec, that.bandwidthBytesPerSec)
            && Objects.equals(slowClose, that.slowClose)
            && Objects.equals(timeout, that.timeout)
            && Objects.equals(resetPeer, that.resetPeer)
            && Objects.equals(slicerChunkSize, that.slicerChunkSize)
            && Objects.equals(limitDataBytes, that.limitDataBytes)
            && Objects.equals(resetMidResponse, that.resetMidResponse)
            && Objects.equals(resetAfterResponseChunks, that.resetAfterResponseChunks)
            && Objects.equals(slowCloseDelay, that.slowCloseDelay)
            && Objects.equals(http2GoAway, that.http2GoAway)
            && Objects.equals(http2GoAwayErrorCode, that.http2GoAwayErrorCode)
            && Objects.equals(http2GoAwayLastStreamId, that.http2GoAwayLastStreamId);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(latencyMs, down, bandwidthBytesPerSec, slowClose, timeout, resetPeer, slicerChunkSize, limitDataBytes,
                resetMidResponse, resetAfterResponseChunks, slowCloseDelay, http2GoAway, http2GoAwayErrorCode, http2GoAwayLastStreamId);
        }
        return hashCode;
    }
}
