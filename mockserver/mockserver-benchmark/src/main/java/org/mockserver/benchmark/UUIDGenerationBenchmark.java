package org.mockserver.benchmark;

import org.mockserver.uuid.UUIDService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Contention micro-benchmark for id generation on the event-log hot path.
 *
 * <p>Models production: every worker thread mints a UUID per log entry (2-3 per request) via {@code
 * LogEntry.id()}. Id-only call sites use {@link UUIDService#getNonSecureUUID()}, backed by per-thread
 * {@code ThreadLocalRandom} with no shared lock, not {@link UUIDService#getUUID()}: its process-wide
 * {@code SecureRandom} native PRNG is {@code synchronized}, so under sustained multi-threaded load all
 * worker event loops would serialise on that one monitor.</p>
 *
 * <p>Run with a high thread count to expose the contention - a single thread never contends and so
 * understates the win to near zero:</p>
 *
 * <pre>./run.sh -t 32 UUIDGenerationBenchmark
 * ./run.sh -prof gc UUIDGenerationBenchmark          # allocation per op (deterministic)
 * ./run.sh UUIDGenerationBenchmark                   # uses the @Threads(32) default below</pre>
 *
 * <p>{@link Mode#Throughput} (ops/us, higher is better) is the headline for the contended lock;
 * {@code secureUUID} vs {@code nonSecureUUID} under many threads shows the cost of the lock. {@code gc.alloc.rate.norm} (B/op) is the deterministic secondary signal.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(32)
public class UUIDGenerationBenchmark {

    /**
     * Cryptographically-secure UUID. Every thread draws from the shared {@code SecureRandom},
     * whose native PRNG monitor serialises them.
     */
    @Benchmark
    public void secureUUID(Blackhole blackhole) {
        blackhole.consume(UUIDService.getUUID());
    }

    /**
     * The id-only path: fast, contention-free UNIQUE UUID from per-thread {@code ThreadLocalRandom}. No
     * shared lock, so throughput should scale with threads where the secure path plateaus.
     */
    @Benchmark
    public void nonSecureUUID(Blackhole blackhole) {
        blackhole.consume(UUIDService.getNonSecureUUID());
    }
}
