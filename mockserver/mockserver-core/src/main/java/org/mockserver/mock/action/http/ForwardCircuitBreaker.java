package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.ClientConfigurationException;
import org.mockserver.httpclient.HeaderLimitExceededException;
import org.mockserver.httpclient.UpstreamProxyUnreachableException;
import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;
import org.mockserver.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.mockserver.formatting.StringFormatter.escapeControlCharactersForLog;

/**
 * Per-upstream circuit breaker for forwarded/proxied requests. State is keyed by upstream
 * {@code host:port}. After {@code forwardProxyCircuitBreakerFailureThreshold} consecutive failures
 * to one upstream the breaker trips <b>open</b> and {@link #allowRequest(String)} fails subsequent
 * requests fast (the caller returns a 503) for {@code forwardProxyCircuitBreakerWindowMillis}. Once
 * the window elapses the breaker moves to <b>half-open</b>: it permits a single trial request. A
 * success ({@link #recordSuccess}) closes the breaker; a failure ({@link #recordFailure}) re-opens it for
 * another window. An outcome that says nothing about the upstream's health ({@link #recordNeutral}) does
 * neither.
 *
 * <p>The whole mechanism is inert unless {@code forwardProxyCircuitBreakerEnabled} is true, so the
 * default behaviour (every request attempted) is unchanged.
 *
 * <p>The singleton instance is shared process-wide, consistent with
 * {@link ChaosAutoHaltMonitor} and {@link LlmCostBudgetMonitor}. The number of currently-open
 * upstreams is exposed via {@link #openCircuitCount()} (and the {@code mock_server_upstream_circuit_open}
 * Prometheus gauge).
 */
public class ForwardCircuitBreaker {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardCircuitBreaker.class);

    private static final ForwardCircuitBreaker INSTANCE = new ForwardCircuitBreaker(TimeService::currentTimeMillis);

    /**
     * State for a single upstream. {@code consecutiveFailures} counts failures since the last
     * success; {@code openedAtMillis} is 0 when the breaker is closed and the open-transition
     * timestamp otherwise; {@code trialInFlight} guards the single half-open trial request so only
     * one request probes the upstream per window.
     */
    static final class UpstreamState {
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicLong openedAtMillis = new AtomicLong(0);
        final AtomicInteger trialInFlight = new AtomicInteger(0);
    }

    private final Map<String, UpstreamState> upstreams = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    ForwardCircuitBreaker(LongSupplier clock) {
        this.clock = clock;
    }

    public static ForwardCircuitBreaker getInstance() {
        return INSTANCE;
    }

    /**
     * Build the stable per-upstream key from a resolved socket address (host and port only — never
     * the path), returning {@code null} when no usable host is available.
     */
    public static String keyFor(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        String host = remoteAddress.getHostString();
        if (host == null || host.isEmpty()) {
            return null;
        }
        return host + ":" + remoteAddress.getPort();
    }

    /**
     * Decide whether a request to the given upstream may proceed.
     * <ul>
     *   <li><b>Closed</b> — always allowed.</li>
     *   <li><b>Open</b> and still within the window — rejected (fail fast).</li>
     *   <li><b>Open</b> but the window has elapsed (half-open) — exactly one trial request is
     *       allowed through; concurrent callers in the same window are rejected.</li>
     * </ul>
     *
     * @param key the upstream key from {@link #keyFor(InetSocketAddress)} (a null/blank key always allows)
     * @return true if the request should be forwarded, false to fail fast with a 503
     */
    public boolean allowRequest(Configuration configuration, String key) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.forwardProxyCircuitBreakerEnabled()) || key == null) {
            return true;
        }
        UpstreamState state = upstreams.get(key);
        if (state == null) {
            return true;
        }
        long openedAt = state.openedAtMillis.get();
        if (openedAt == 0) {
            // closed
            return true;
        }
        long windowMillis = configuration.forwardProxyCircuitBreakerWindowMillis();
        if (clock.getAsLong() - openedAt < windowMillis) {
            // open and within the window — fail fast
            return false;
        }
        // half-open: allow a single trial request through per window
        return state.trialInFlight.compareAndSet(0, 1);
    }

    /**
     * Record a successful forward to the given upstream. Closes the breaker and clears the failure
     * count. No-op when the breaker is disabled or the key is null.
     *
     * <p>A fully-healthy upstream (closed, zero failures, no trial in flight) is evicted from the
     * per-upstream map so a steadily-succeeding upstream leaves no permanent footprint. This bounds
     * the map to currently-degraded upstreams and closes the only growth vector — distinct keys
     * (e.g. via varied client {@code Host} headers when no explicit remote address is supplied).
     * Eviction is value-conditional ({@link ConcurrentHashMap#remove(Object, Object)}) so a state
     * whose map entry has been replaced is never dropped, and is guarded on the state still being
     * fully healthy. The only thing a concurrent {@link #recordFailure} can lose to this is an
     * in-place sub-threshold failure increment on a still-closed breaker, which is benign: the
     * breaker counts consecutive failures since the last success, so a success legitimately resets
     * that count. An entry that a concurrent failure trips open is retained (the health re-check
     * sees {@code openedAtMillis != 0}).
     */
    public void recordSuccess(Configuration configuration, String key) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.forwardProxyCircuitBreakerEnabled()) || key == null) {
            return;
        }
        UpstreamState state = upstreams.get(key);
        if (state != null) {
            boolean wasOpen = state.openedAtMillis.getAndSet(0) != 0;
            state.consecutiveFailures.set(0);
            state.trialInFlight.set(0);
            if (wasOpen) {
                LOG.info("forward circuit breaker closed for upstream {} after a successful trial request", escapeControlCharactersForLog(key));
            }
            // Evict only if still fully healthy; if a concurrent failure mutated the state the
            // value-conditional remove is a no-op and the entry (correctly) survives.
            if (state.openedAtMillis.get() == 0 && state.consecutiveFailures.get() == 0 && state.trialInFlight.get() == 0) {
                upstreams.remove(key, state);
            }
        }
    }

    /**
     * Record a failed forward to the given upstream. Increments the consecutive-failure count and
     * trips the breaker open once the configured threshold is reached (or immediately re-opens it on
     * a failed half-open trial). No-op when the breaker is disabled or the key is null.
     */
    public void recordFailure(Configuration configuration, String key) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.forwardProxyCircuitBreakerEnabled()) || key == null) {
            return;
        }
        UpstreamState state = upstreams.computeIfAbsent(key, k -> new UpstreamState());
        // A failed half-open trial re-opens immediately for another window.
        if (state.trialInFlight.compareAndSet(1, 0) && state.openedAtMillis.get() != 0) {
            state.openedAtMillis.set(clock.getAsLong());
            LOG.warn("forward circuit breaker re-opened for upstream {} after a failed half-open trial request", escapeControlCharactersForLog(key));
            return;
        }
        int failures = state.consecutiveFailures.incrementAndGet();
        int threshold = configuration.forwardProxyCircuitBreakerFailureThreshold();
        if (failures >= threshold && state.openedAtMillis.compareAndSet(0, clock.getAsLong())) {
            LOG.warn(
                "forward circuit breaker opened for upstream {} after {} consecutive failures (threshold {}) — "
                    + "failing requests fast for {} ms",
                escapeControlCharactersForLog(key),
                failures,
                threshold,
                configuration.forwardProxyCircuitBreakerWindowMillis()
            );
        }
    }

    /**
     * Record a forward whose outcome says nothing about the upstream's health: a configuration error, which never
     * reached the upstream, or a refusal of the upstream's response headers for {@code maxHeaderSize}. The breaker's
     * state and failure count are left as they were; only a half-open trial is released, so the next request probes
     * the upstream. No-op when the breaker is disabled or the key is null.
     */
    public void recordNeutral(Configuration configuration, String key) {
        if (configuration == null || !Boolean.TRUE.equals(configuration.forwardProxyCircuitBreakerEnabled()) || key == null) {
            return;
        }
        UpstreamState state = upstreams.get(key);
        if (state != null) {
            state.trialInFlight.set(0);
        }
    }

    /**
     * Whether a forward that failed with {@code throwable} says nothing about the upstream's health, so is recorded
     * with {@link #recordNeutral}: a configuration error, a header-limit refusal, a target
     * forwardProxyBlockPrivateNetworks refused, or an upstream proxy that could not be reached.
     */
    public static boolean isNeutral(Throwable throwable) {
        return throwable != null
            && (ClientConfigurationException.in(throwable) != null || HeaderLimitExceededException.in(throwable) != null
            || ForwardTargetBlockedException.in(throwable) != null || UpstreamProxyUnreachableException.in(throwable) != null);
    }

    /**
     * Number of upstreams whose breaker is currently open (in the open or half-open state). Backs
     * the {@code mock_server_upstream_circuit_open} gauge. Counts an upstream as open whenever its
     * open timestamp is non-zero, regardless of whether the window has elapsed, so the gauge
     * reflects "this upstream is currently degraded" until a trial request closes it.
     */
    public int openCircuitCount() {
        int count = 0;
        for (UpstreamState state : upstreams.values()) {
            if (state.openedAtMillis.get() != 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * True when the breaker for the given key is currently open (open timestamp set). Test/diagnostic helper.
     */
    public boolean isOpen(String key) {
        UpstreamState state = upstreams.get(key);
        return state != null && state.openedAtMillis.get() != 0;
    }

    /**
     * Number of upstreams currently tracked in the per-upstream map. A steadily-healthy upstream is
     * evicted on success so this stays bounded to degraded/being-probed upstreams. Test/diagnostic helper.
     */
    int trackedUpstreamCount() {
        return upstreams.size();
    }

    /**
     * Reset all per-upstream state. Called on server reset and for test isolation.
     */
    public void reset() {
        upstreams.clear();
    }
}
