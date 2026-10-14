package org.mockserver.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Harness isolating the cost of the off-event-loop local-callback dispatch hop.
 *
 * <p>Production ({@code org.mockserver.scheduler.Scheduler}) dispatches every LOCAL (in-JVM)
 * object/class callback in asynchronous/Netty mode onto a dedicated, UNBOUNDED cached pool —
 * {@code new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60s, new SynchronousQueue<>(), "MockServer-LocalCallback")}
 * — via {@code localCallbackExecutor.execute(() -> run(command, port))}, rather than running the
 * callback inline on the server worker event loop. That hand-off keeps a pooled upstream channel,
 * reused inside a synchronous loopback callback, from pinning (and self-deadlocking) the worker
 * thread. The hop is not free: it costs one thread wake-up + scheduling per local callback.
 *
 * <p>This benchmark reproduces that exact executor configuration and measures the added latency of
 * ONE dispatch + completion round-trip ({@code offLoopDispatch}) against running the same trivial
 * task inline ({@code inline}). The DELTA between the two is the per-local-callback thread-hop
 * overhead. A real local callback also does the response work itself; this isolates only the hop so
 * the number is the marginal cost of the hop, not the callback's own cost.
 *
 * <p>Single-threaded by design: it measures the latency of a SINGLE hop on the critical path of one
 * request, which is what a user perceives per local-callback request (concurrency is exercised by
 * the k6 callback-under-load run, not here).
 *
 * <pre>./run.sh LocalCallbackDispatchBenchmark -f 1 -wi 3 -i 5</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LocalCallbackDispatchBenchmark {

    /** EXACT production configuration: unbounded cached pool, 0 core, 60s keep-alive, SynchronousQueue. */
    private ThreadPoolExecutor localCallbackExecutor;

    @Setup(Level.Trial)
    public void setup() {
        final AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "MockServer-LocalCallback" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        localCallbackExecutor = new ThreadPoolExecutor(
            0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            tf
        );
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        localCallbackExecutor.shutdownNow();
    }

    /** A trivial unit of "callback" work, identical in both arms so the delta is purely the hop. */
    private static long work(long x) {
        // a few cheap ops so the JIT cannot fold the call away entirely
        return (x ^ (x << 7)) + 1;
    }

    /** Baseline: run the unit of work inline on the calling thread (no hop). */
    @Benchmark
    public long inline() {
        return work(System.nanoTime());
    }

    /**
     * The production hop: dispatch the unit of work onto the unbounded cached pool and block for its
     * result — mirroring how the worker thread hands a local callback off and the request completes
     * once the callback (off-loop) produces its response. The get() makes the round-trip observable
     * so the measured time includes the thread wake-up + scheduling latency of the hand-off.
     */
    @Benchmark
    public long offLoopDispatch() throws ExecutionException, InterruptedException {
        CompletableFuture<Long> done = new CompletableFuture<>();
        localCallbackExecutor.execute(() -> done.complete(work(System.nanoTime())));
        return done.get();
    }

    /** Sink so the JIT keeps the result live (defensive; AverageTime already consumes the return). */
    @Benchmark
    public void offLoopDispatchConsumed(Blackhole bh) throws ExecutionException, InterruptedException {
        CompletableFuture<Long> done = new CompletableFuture<>();
        localCallbackExecutor.execute(() -> done.complete(work(System.nanoTime())));
        bh.consume(done.get());
    }
}
