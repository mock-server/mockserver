package org.mockserver.benchmark;

import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.slf4j.event.Level.INFO;

/**
 * The wall-clock cost of ONE event-log query ({@code retrieveRequests}) as a function
 * of log occupancy {@code n}.
 *
 * <p><b>The mechanism under test.</b> A query is dispatched as a {@code RUNNABLE} onto the event
 * log's SINGLE disruptor consumer thread, where it runs an O(n) scan over the retained entries, each
 * entry cloned and run through a full request-match. So query latency grows linearly with occupancy
 * and — because that one thread also appends serving-path writes — a long scan freezes ingestion for
 * its whole duration (the correctness consequence proven separately and deterministically by
 * {@link org.mockserver.log.EventLogQueryDropProof}: while the consumer is frozen, serving-path
 * writes published via {@code tryPublishEvent} overflow the ring and are dropped).
 *
 * <p><b>What this benchmark measures.</b> {@code AverageTime} per query at occupancies spanning the
 * default {@code maxLogEntries} ceiling (250_000) and above. The latency at occupancy {@code n} is
 * the window for which the consumer is frozen; dividing the ring size (16_384) by it gives the
 * sustained serving-path write rate above which a single concurrent query drops evidence.
 *
 * <p><b>No core scaling.</b> Running with {@code -t K} does NOT reduce per-query latency: every
 * query serializes on the one consumer thread regardless of how many caller threads enqueue them.
 * The {@link org.mockserver.log.EventLogQueryDropProof} {@code [g2:serialize]} line quantifies this
 * (K concurrent queries take ~K x a single scan). The benchmark is therefore single-threaded by
 * default; a multi-thread run only demonstrates the absence of scaling.
 *
 * <p><b>Proof the mechanism (drop + serialization + non-blocking writes), not assumed</b>, lives in
 * {@code org.mockserver.log.EventLogQueryDropProof} — run it before trusting these numbers.
 *
 * <pre>./run.sh EventLogQueryLatencyBenchmark -f 1 -wi 3 -i 5 -t 1</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class EventLogQueryLatencyBenchmark {

    private static final int RING = 16_384;

    @Param({"10000", "50000", "100000", "250000"})
    public int n;

    private MockServerEventLog eventLog;

    @Setup(Level.Trial)
    public void setup() {
        ConfigurationProperties.logLevel("WARN");
        // maxLogEntries well above n so eviction never confounds the scan length; ring pinned to the
        // real default so the derived write-rate-to-overflow figure is the shipped one.
        Configuration configuration = configuration().maxLogEntries(Math.max(n * 2, 1_000_000)).ringBufferSize(RING);
        eventLog = new MockServerEventLog(
            configuration,
            new MockServerLogger(configuration, EventLogQueryLatencyBenchmark.class),
            mock(Scheduler.class),
            true
        );
        // Fill in chunks, draining between them so the fill itself never overflows the ring.
        int chunk = RING / 4;
        int written = 0;
        while (written < n) {
            int c = Math.min(chunk, n - written);
            for (int i = 0; i < c; i++) {
                eventLog.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(INFO).setHttpRequest(request("/req/" + (written + i))));
            }
            written += c;
            awaitDrain();
        }
        if (eventLog.getDroppedLogEventCount() != 0) {
            throw new IllegalStateException("fill dropped " + eventLog.getDroppedLogEventCount() + " entries — occupancy would be understated");
        }
        if (eventLog.getRetainedEntryCount() < n) {
            throw new IllegalStateException("retained " + eventLog.getRetainedEntryCount() + " < requested " + n);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        eventLog.stop();
    }

    /**
     * One full query: dispatch {@code retrieveRequests} and block until its scan completes on the
     * consumer thread. The awaited latch is completed from inside the {@code RUNNABLE}, so the
     * measured time is enqueue + the O(n) scan.
     */
    @Benchmark
    public int retrieveRequests() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final int[] count = new int[1];
        eventLog.retrieveRequests(request(), results -> {
            count[0] = results.size();
            done.countDown();
        });
        done.await();
        return count[0];
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (eventLog.getRingBufferOccupancy() > 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(500_000);
        }
    }
}
