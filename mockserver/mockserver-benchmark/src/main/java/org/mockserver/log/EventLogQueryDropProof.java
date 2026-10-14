package org.mockserver.log;

import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;

/**
 * Mechanism proof (NOT a JMH benchmark) — a standalone verifier that a concurrent event-log
 * QUERY (retrieve/verify) can, by monopolising the single disruptor consumer thread, cause
 * serving-path log writes to be DROPPED, silently losing the {@code RECEIVED_REQUEST} evidence a
 * later {@code verify} needs. It lives in {@code org.mockserver.log} so it can build a real
 * {@link MockServerEventLog} through its public API and read {@link MockServerEventLog#getDroppedLogEventCount()}
 * / {@link MockServerEventLog#getRingBufferOccupancy()}.
 *
 * <p><b>The mechanism under test.</b> {@code MockServerEventLog} runs its disruptor with a SINGLE
 * handler that both APPENDS serving-path entries and RUNS every query (retrieve/verify/clear are
 * dispatched as {@code RUNNABLE} events onto that same consumer thread). A query is an O(n) scan
 * over up to {@code maxLogEntries} entries, each running a full cloned request-match. While that
 * scan monopolises the consumer, serving-path writes — published via the non-blocking
 * {@code tryPublishEvent}, which DROPS on a full ring rather than blocking — back up; once the ring
 * fills they are dropped with only a WARN-once and a bump of {@code mock_server_dropped_log_events}.
 * A user can therefore lose verification evidence, with no error, purely because a retrieval was in
 * flight.
 *
 * <p><b>Two checks, each with its own counterfactual</b> — a number that would look the same whether
 * or not the mechanism fired proves nothing, so each check pairs the suspected-cause arm with a
 * control that removes only the cause:
 *
 * <ol>
 *   <li><b>Held consumer vs free consumer (deterministic, latch-based).</b> With the consumer frozen
 *       by a blocking {@code RUNNABLE}, a fixed flood of {@code W > ringSize} serving-path writes
 *       drops {@code ~W - ringSize} entries; with the consumer FREE the identical flood drops zero
 *       (it drains as fast as it is published). This isolates the drop path itself from write volume
 *       — the flood count is held constant across the two arms, only the hold is removed. This is the
 *       same drop path {@link MockServerEventLogDroppedEventsTest} pins; it is reproduced here as the
 *       race-free floor the second (real-query) check builds on.</li>
 *   <li><b>Real query vs no query.</b> The log is filled to a large occupancy N and a
 *       paced writer publishes at a rate a FREE consumer sustains with zero drops (the control arm
 *       proves it does). Re-running that identical paced writer while a REAL {@code retrieveRequests}
 *       scan is in flight drops entries — because the scan freezes the consumer for its whole
 *       duration and the paced writes accumulate past the ring. Same writer, same rate, same
 *       occupancy: the only difference is the query, so a drop in the query arm and none in the
 *       control arm is caused BY the query.</li>
 * </ol>
 *
 * <p>Run standalone (prints {@code PROOF: PASS} / {@code PROOF: FAIL}, exits non-zero on failure):
 * <pre>java -cp "target/classes:$(cat target/classpath.txt)" org.mockserver.log.EventLogQueryDropProof</pre>
 */
public final class EventLogQueryDropProof {

    private EventLogQueryDropProof() {
    }

    private static final int RING = 16_384; // real default ring (min(maxLogEntries,16384), pow2)

    public static void main(String[] args) throws Exception {
        // Keep console noise down; RECEIVED_REQUEST is retained at every level (see MockServerEventLog
        // javadoc), so a WARN console level does not affect what the log stores or a query scans.
        ConfigurationProperties.logLevel("WARN");

        boolean ok = true;
        ok &= proveHeldVsFreeConsumer();
        ok &= proveRealQueryVsNoQuery();
        ok &= proveServingWritesNonBlockingDuringQuery();
        // Diagnostics (not PASS/FAIL): quantify the boundary and the serialization claim.
        scanLatencyVsOccupancy();
        querySerializationVsCores();
        System.out.println(ok ? "PROOF: PASS" : "PROOF: FAIL");
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * A long query must NOT block the serving path's log write.
     * {@code add()} publishes via {@code tryPublishEvent}, which returns immediately whether the ring
     * accepts (fast) or is full (drop) — it never blocks on the consumer. (Request MATCHING itself
     * never reads the event log at all — a structural decoupling, so it is unaffected by construction;
     * this check covers the one serving-path touch-point that does reach the log: the write.)
     */
    private static boolean proveServingWritesNonBlockingDuringQuery() throws Exception {
        final int occupancy = 400_000;
        MockServerEventLog log = newLog(1_000_000, RING);
        try {
            fillTo(log, occupancy);

            // Baseline: max single-write latency with the consumer idle (ring not full).
            long idleMaxNanos = maxWriteLatencyNanos(log, 5_000);
            awaitDrain(log);

            // During a scan: fire the query, then time writes. Even when the ring fills and writes
            // start dropping, each add() must still return promptly (non-blocking drop).
            log.retrieveRequests(request(), r -> { });
            long busyMaxNanos = maxWriteLatencyNanos(log, 5_000);
            awaitDrain(log);

            double idleUs = idleMaxNanos / 1_000.0;
            double busyUs = busyMaxNanos / 1_000.0;
            // Generous ceiling: a non-blocking write, even contended, stays sub-millisecond; a write
            // that BLOCKED on the scan would be orders of magnitude over this.
            boolean pass = busyMaxNanos < TimeUnit.MILLISECONDS.toNanos(5);
            System.out.printf("[g2:nonblock] maxWrite idle=%.1fus during-scan=%.1fus (ceiling 5000us) -> %s%n",
                idleUs, busyUs, pass ? "PASS" : "FAIL");
            return pass;
        } finally {
            log.stop();
        }
    }

    /**
     * Check 1: with the consumer HELD, a fixed flood overflows the ring and drops; with the consumer
     * FREE the identical flood drops nothing. The flood count is constant — only the hold changes.
     */
    private static boolean proveHeldVsFreeConsumer() throws Exception {
        final int flood = RING * 3; // comfortably larger than the ring
        MockServerEventLog log = newLog(1_000_000, RING);
        try {
            // ---- HELD arm ----
            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            log.add(new LogEntry().setType(LogEntry.LogMessageType.RUNNABLE).setConsumer(() -> {
                started.countDown();
                await(hold, 30);
            }));
            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("hold consumer never started");
            }
            long before = log.getDroppedLogEventCount();
            for (int i = 0; i < flood; i++) {
                log.add(received(i));
            }
            long heldDrops = log.getDroppedLogEventCount() - before;
            hold.countDown();
            awaitDrain(log);

            // ---- FREE arm (counterfactual: identical flood, consumer never held) ----
            long before2 = log.getDroppedLogEventCount();
            for (int i = 0; i < flood; i++) {
                log.add(received(i));
            }
            awaitDrain(log);
            long freeDrops = log.getDroppedLogEventCount() - before2;

            boolean pass = heldDrops > 0 && freeDrops == 0;
            System.out.println("[g2:held] ring=" + RING + " flood=" + flood
                + " heldDrops=" + heldDrops + " (expected ~" + (flood - RING) + ", >0)"
                + " | freeDrops=" + freeDrops + " (expected 0) -> " + (pass ? "PASS" : "FAIL"));
            return pass;
        } finally {
            log.stop();
        }
    }

    /**
     * Check 2: a paced writer that a free consumer sustains with zero drops (control)
     * drops entries when an identical run coincides with a real O(n) query scan.
     */
    private static boolean proveRealQueryVsNoQuery() throws Exception {
        final int occupancy = 400_000;   // large log to make one scan long
        MockServerEventLog log = newLog(1_000_000, RING);
        try {
            fillTo(log, occupancy);
            long fillDrops = log.getDroppedLogEventCount();
            if (fillDrops != 0) {
                System.out.println("[g2:query] WARN: " + fillDrops + " drops during fill — fill pacing too aggressive");
            }

            // Measure one scan's wall-clock.
            long scanNanos = timeOneScan(log);
            double scanMs = scanNanos / 1_000_000.0;

            // Paced writer: publish `writes` entries, pausing `pauseMicros` every `chunk`. Tuned so a
            // FREE consumer keeps up (control drops == 0) but the total spans longer than one scan so
            // a frozen consumer accumulates past the ring.
            final int writes = 200_000;
            final int chunk = 500;
            final long pauseMicros = 50;

            // ---- CONTROL arm: paced writer, NO query ----
            long beforeC = log.getDroppedLogEventCount();
            long tC = pacedWrite(log, writes, chunk, pauseMicros);
            awaitDrain(log);
            long controlDrops = log.getDroppedLogEventCount() - beforeC;

            // ---- QUERY arm: identical paced writer, a real scan fired first ----
            long beforeQ = log.getDroppedLogEventCount();
            // fire real query asynchronously; it occupies the consumer for ~scanNanos
            log.retrieveRequests(request(), r -> { /* result discarded */ });
            long tQ = pacedWrite(log, writes, chunk, pauseMicros);
            awaitDrain(log);
            long queryDrops = log.getDroppedLogEventCount() - beforeQ;

            boolean pass = queryDrops > 0 && controlDrops == 0;
            System.out.printf("[g2:query] occupancy=%d scan=%.1fms writer=%dms(control)/%dms(query)%n",
                occupancy, scanMs, tC / 1_000_000, tQ / 1_000_000);
            System.out.println("[g2:query] controlDrops=" + controlDrops + " (expected 0)"
                + " | queryDrops=" + queryDrops + " (expected >0) -> " + (pass ? "PASS" : "FAIL"));
            return pass;
        } finally {
            log.stop();
        }
    }

    /**
     * Diagnostic (boundary): scan wall-clock grows ~linearly with occupancy. The derived
     * "fill rate" column is RING / scan = the sustained serving-path write rate (entries/s) that
     * would exactly fill the ring during one scan — above it, a single concurrent query drops. Note
     * the default maxLogEntries ceiling is 250_000; higher occupancies model raised-limit deployments.
     */
    private static void scanLatencyVsOccupancy() {
        int[] occupancies = {10_000, 50_000, 100_000, 250_000, 500_000};
        System.out.println("[g2:latency] occupancy | scanMs | writes/s to fill ring during one scan");
        for (int occ : occupancies) {
            MockServerEventLog log = newLog(1_000_000, RING);
            try {
                fillTo(log, occ);
                long scanNanos = timeOneScan(log);
                double scanMs = scanNanos / 1_000_000.0;
                double fillRate = RING / (scanNanos / 1_000_000_000.0);
                System.out.printf("[g2:latency] %9d | %6.1f | %,.0f entries/s%n", occ, scanMs, fillRate);
            } finally {
                log.stop();
            }
        }
    }

    /**
     * Diagnostic: query throughput cannot scale with cores because every query runs on the
     * one disruptor consumer thread. Firing K queries concurrently from K caller threads takes about
     * the same wall-clock as running them one after another (K x single-scan) — the caller threads
     * only enqueue; the scans serialize on the consumer. Reported as a serialization ratio
     * (concurrent-wall / single-scan); ~K means fully serialized, ~1 would mean perfect scaling.
     */
    private static void querySerializationVsCores() {
        final int occupancy = 200_000;
        MockServerEventLog log = newLog(1_000_000, RING);
        try {
            fillTo(log, occupancy);
            long single = timeOneScan(log);
            int k = Math.max(4, Runtime.getRuntime().availableProcessors());
            CountDownLatch done = new CountDownLatch(k);
            long t0 = System.nanoTime();
            for (int i = 0; i < k; i++) {
                Thread t = new Thread(() -> log.retrieveRequests(request(), r -> done.countDown()));
                t.setDaemon(true);
                t.start();
            }
            await(done, 120);
            long concurrent = System.nanoTime() - t0;
            double ratio = (double) concurrent / single;
            System.out.printf("[g2:serialize] cores=%d single-scan=%.1fms %d-concurrent-wall=%.1fms ratio=%.1fx (>~%d => serialized, not scaling)%n",
                Runtime.getRuntime().availableProcessors(), single / 1_000_000.0, k, concurrent / 1_000_000.0, ratio, k);
        } finally {
            log.stop();
        }
    }

    // ---- helpers ----

    /** Publish {@code n} writes back-to-back, returning the maximum single-write latency (ns). */
    private static long maxWriteLatencyNanos(MockServerEventLog log, int n) {
        long max = 0;
        for (int i = 0; i < n; i++) {
            long s = System.nanoTime();
            log.add(received(i));
            long d = System.nanoTime() - s;
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    private static MockServerEventLog newLog(int maxLogEntries, int ringBufferSize) {
        Configuration c = configuration().maxLogEntries(maxLogEntries).ringBufferSize(ringBufferSize);
        return new MockServerEventLog(c, new MockServerLogger(c, MockServerEventLog.class), mock(Scheduler.class), true);
    }

    private static LogEntry received(int i) {
        return new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request("/req/" + i));
    }

    /** Fill the retained deque to n entries in chunks, draining between chunks so the fill never drops. */
    private static void fillTo(MockServerEventLog log, int n) {
        int chunk = RING / 4;
        int written = 0;
        while (written < n) {
            int c = Math.min(chunk, n - written);
            for (int i = 0; i < c; i++) {
                log.add(received(written + i));
            }
            written += c;
            awaitDrain(log);
        }
    }

    private static long timeOneScan(MockServerEventLog log) {
        // warm once, then time
        for (int w = 0; w < 2; w++) {
            CountDownLatch done = new CountDownLatch(1);
            log.retrieveRequests(request(), r -> done.countDown());
            await(done, 60);
        }
        long t0 = System.nanoTime();
        CountDownLatch done = new CountDownLatch(1);
        log.retrieveRequests(request(), r -> done.countDown());
        await(done, 60);
        return System.nanoTime() - t0;
    }

    private static long pacedWrite(MockServerEventLog log, int writes, int chunk, long pauseMicros) {
        long t0 = System.nanoTime();
        for (int i = 0; i < writes; i++) {
            log.add(received(i));
            if (chunk > 0 && (i % chunk) == (chunk - 1)) {
                LockSupport.parkNanos(pauseMicros * 1_000);
            }
        }
        return System.nanoTime() - t0;
    }

    private static void awaitDrain(MockServerEventLog log) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (log.getRingBufferOccupancy() > 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(500_000);
        }
    }

    private static void await(CountDownLatch latch, int seconds) {
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
