package org.mockserver.benchmark;

import org.mockserver.matchers.MatchingTimeoutExecutor;
import org.mockserver.matchers.MatchingTimeoutHandoffProof;
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

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * The cost of the per-match hand-off to the shared matching thread pool, versus
 * evaluating the same regex inline.
 *
 * <p><b>The mechanism under test.</b> Every non-literal matcher (regex path/method/header/query/
 * string-body, XPath, GraphQL {@code operationName}, JSON-RPC {@code method}, LLM conversation)
 * routes its evaluation through {@link MatchingTimeoutExecutor#callWithTimeout}, which submits the
 * real match to a JVM-wide {@code ThreadPoolExecutor} (SynchronousQueue, cap
 * {@code max(64, cores*16)}) and then <em>blocks the calling thread</em> on
 * {@code future.get(timeoutMillis, ...)}. The caller is a Netty event-loop thread (the
 * {@code HttpRequestHandler} is added to the pipeline with no {@code EventExecutorGroup}), and there
 * are a fixed 5 of those. It is on by default: {@code regexMatchingTimeoutMillis()} returns 5000.
 * The question is whether that hand-off — a thread wake-up, a {@code Future}, and a cross-thread
 * result copy for a match that is itself microscopically cheap — is a measurable tax, and whether it
 * worsens with concurrency.
 *
 * <p><b>What this benchmark measures.</b> {@code callWithTimeout} wrapping a representative-cheap
 * regex ({@code ^/regex/path-[a-z]+-\d+$} against a matching input), in two arms:
 * <ul>
 *   <li>{@code mode=POOL} — {@code timeoutMillis=5000}, the shipped default: the match is submitted
 *       to the pool and the caller blocks on the future. This is the production path.</li>
 *   <li>{@code mode=INLINE} — {@code timeoutMillis=0}: {@code callWithTimeout}'s first line
 *       ({@code if (timeoutMillis <= 0) return task.call();}) evaluates the regex on the calling
 *       thread with no pool, no future, no cross-thread hop. This is the control, and it is exactly
 *       what setting {@code mockserver.regexMatchingTimeoutMillis=0} does in production (0 is not
 *       a magic sentinel, it is the {@code <= 0} disable branch).</li>
 * </ul>
 *
 * <p><b>Why call {@code callWithTimeout} directly</b> rather than driving {@code RegexStringMatcher}
 * or {@code RequestMatchers}: the question is about the executor hand-off specifically, so the
 * benchmark isolates that single operation around a fixed, cheap payload. Driving the full matcher
 * would fold in {@code NottableString} allocation, the pure-ASCII-literal short-circuit, and the
 * candidate-index scan — noise that the sibling {@code MatchingBenchmark} / {@code
 * CandidateIndexChurnBenchmark} already measure and that would dilute the hand-off signal. The
 * executor reached here is the identical {@code static} instance every production matcher submits
 * to; {@link MatchingTimeoutHandoffProof} confirms the real regex matchers call this same path.
 *
 * <p><b>A separate class, deliberately.</b> {@code MatchingBenchmark} is consumed by a daily CI gate
 * ({@code microbench.*.time_per_op} / {@code alloc_bytes_per_op}); adding a {@code @Threads} shape or
 * a new param to it would perturb that baseline and collide its result keys. This is a standalone
 * research benchmark, wired into no gate.
 *
 * <p><b>Threads.</b> The finding is about event-loop blocking and pool contention, so the thread
 * count is the primary dimension — set on the command line ({@code -t 1}, {@code -t 4}, {@code -t 8}),
 * exactly as {@code CandidateIndexChurnBenchmark} does. {@code @Threads} is a single-valued annotation and cannot
 * be swept as a JMH param, so the run script drives the three counts as separate (sequential) forks.
 * At {@code -t 8} the peak in-flight task count is 8, far below the {@code max(64, cores*16)} pool
 * cap, so this benchmark never saturates the pool (confirmed by the per-trial {@code [g6]} line and
 * by {@link MatchingTimeoutHandoffProof}'s saturation check) — it measures hand-off cost under
 * contention, not the inline-fallback path.
 *
 * <p><b>Proof the arms take the path they claim</b> lives in {@link MatchingTimeoutHandoffProof}
 * (run first by {@code run-g6-handoff.sh}). In addition, the {@code [g6]} line printed at each trial
 * teardown reports the pool submissions that landed during that trial: a POOL trial reports a large
 * count (one per op, warmup included), an INLINE trial reports zero — a per-trial guard against a
 * silent false result from stale classes or a mis-set timeout.
 *
 * <pre>./run-g6-handoff.sh            # proof + JMH at -t 1,4,8 with -prof gc
 * ./run.sh MatchingTimeoutHandoffBenchmark -prof gc -f 1 -wi 5 -i 5 -t 4</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MatchingTimeoutHandoffBenchmark {

    @Param({"POOL", "INLINE"})
    public String mode;

    /** POOL -> 5000 (shipped default, submit + block on future); INLINE -> 0 (evaluate on caller). */
    private long timeoutMillis;

    /** A representative path regex (metacharacters, so it is a genuine regex, not a literal). */
    private final Pattern pattern = Pattern.compile("^/regex/path-[a-z]+-\\d+$");
    private final String input = "/regex/path-abcdef-42";

    private long submittedAtSetup;

    @Setup(Level.Trial)
    public void setup() {
        timeoutMillis = "POOL".equals(mode) ? 5000L : 0L;
        submittedAtSetup = MatchingTimeoutHandoffProof.submittedTaskCount();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Per-trial mechanism observation: how many matches actually went through the pool during
        // this trial (warmup + measurement, across all -t threads). POOL must be large and > 0;
        // INLINE must be 0. A zero here in the POOL arm, or a non-zero in the INLINE arm, means the
        // benchmark did NOT measure what it claims (stale classes / mis-set timeout) — a loud
        // false-result guard, mirroring CandidateIndexChurnBenchmark's [churn] line.
        long delta = MatchingTimeoutHandoffProof.submittedTaskCount() - submittedAtSetup;
        System.out.println("[g6] mode=" + mode + " timeoutMillis=" + timeoutMillis
            + " poolSubmissionsThisTrial=" + delta
            + (("POOL".equals(mode)) ? " (expected > 0)" : " (expected 0)"));
    }

    @Benchmark
    public boolean handoff() throws Exception {
        // returning the result keeps JMH from eliminating the call
        return MatchingTimeoutExecutor.callWithTimeout(
            () -> pattern.matcher(input).matches(),
            timeoutMillis,
            Boolean.FALSE,
            null
        );
    }
}
