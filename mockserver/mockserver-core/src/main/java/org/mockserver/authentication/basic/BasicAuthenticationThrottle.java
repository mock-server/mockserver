package org.mockserver.authentication.basic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Slows down password guessing against control-plane Basic authentication. A source may fail
 * {@value #FREE_FAILURES} times; after that each further failure blocks it for twice as long as the last
 * (one second, then two, four, ...) up to {@link #MAX_BACKOFF_MILLIS}. While blocked, its requests are refused
 * without checking a password, so guessing costs no hashing either. A success forgets the source, and so does
 * {@link #FORGET_AFTER_MILLIS} without a failure.
 * <p>
 * The state is bounded: at most {@link #MAX_TRACKED_SOURCES} sources are tracked, the least recently seen
 * being dropped first.
 */
final class BasicAuthenticationThrottle {

    static final int FREE_FAILURES = 5;
    static final long BASE_BACKOFF_MILLIS = 1_000;
    static final long MAX_BACKOFF_MILLIS = 60_000;
    static final long FORGET_AFTER_MILLIS = 15 * 60_000;
    static final int MAX_TRACKED_SOURCES = 4_096;

    private final LongSupplier clockMillis;
    private final Map<String, Failures> failures = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Failures> eldest) {
            return size() > MAX_TRACKED_SOURCES;
        }
    };

    BasicAuthenticationThrottle(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /**
     * @return how long {@code source} must wait before its credentials are checked again, 0 when they may be now
     */
    synchronized long retryAfterMillis(String source) {
        Failures record = current(source, clockMillis.getAsLong());
        return record == null ? 0 : Math.max(0, record.blockedUntilMillis - clockMillis.getAsLong());
    }

    synchronized void failed(String source) {
        long now = clockMillis.getAsLong();
        Failures record = current(source, now);
        if (record == null) {
            record = new Failures();
            failures.put(source, record);
        }
        record.count++;
        record.lastFailureMillis = now;
        if (record.count >= FREE_FAILURES) {
            int doublings = Math.min(record.count - FREE_FAILURES, 16);
            record.blockedUntilMillis = now + Math.min(BASE_BACKOFF_MILLIS << doublings, MAX_BACKOFF_MILLIS);
        }
    }

    synchronized void succeeded(String source) {
        failures.remove(source);
    }

    synchronized int trackedSources() {
        return failures.size();
    }

    synchronized java.util.Set<String> trackedSourceKeys() {
        return new java.util.HashSet<>(failures.keySet());
    }

    private Failures current(String source, long now) {
        Failures record = failures.get(source);
        if (record != null && now - record.lastFailureMillis > FORGET_AFTER_MILLIS) {
            failures.remove(source);
            return null;
        }
        return record;
    }

    private static final class Failures {
        private int count;
        private long lastFailureMillis;
        private long blockedUntilMillis;
    }
}
