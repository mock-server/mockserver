package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.HttpRequest;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Candidate index vs full linear scan for
 * {@link RequestMatchers#firstMatchingExpectation}.
 *
 * <p>{@code indexMode} selects the two code paths to compare directly, isolating the
 * change to a single variable:
 * <ul>
 *   <li>{@code SCAN} — the candidate index is disabled (threshold set above the largest
 *       n), so the UNTOUCHED full linear scan runs. This is the BEFORE baseline.</li>
 *   <li>{@code INDEX} — the candidate index is enabled (threshold 2), so the narrowed
 *       candidate scan runs. This is the AFTER measurement.</li>
 * </ul>
 *
 * <p>{@code n} sweeps small (1/2/5) and large (100/1000/5000) expectation counts to prove
 * BOTH guarantees: small-n must show NO regression (SCAN vs INDEX within noise — and below
 * the production threshold the real product runs SCAN regardless), and large-n must show a
 * clear INDEX improvement. {@code outcome} runs both a HIT (the request matches a literal
 * expectation) and a MISS (matches nothing — the worst case for the full scan). {@code shape}
 * covers a pure-literal set and a regex-heavy set (where every expectation is an
 * un-bucketable regex, so the candidate set degenerates to the full fallthrough — the index
 * must not regress this adversarial case).
 *
 * <pre>./run.sh CandidateIndexBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CandidateIndexBenchmark {

    @Param({"1", "2", "5", "100", "1000", "5000"})
    public int n;

    @Param({"SCAN", "INDEX"})
    public String indexMode;

    @Param({"HIT", "MISS"})
    public String outcome;

    @Param({"LITERAL", "REGEX_HEAVY"})
    public String shape;

    private RequestMatchers requestMatchers;
    private HttpRequest probe;

    @Setup(Level.Trial)
    public void setup() {
        ConfigurationProperties.logLevel("WARN");
        ConfigurationProperties.detailedMatchFailures(false);

        Configuration configuration = Configuration.configuration();
        requestMatchers = new RequestMatchers(
            configuration,
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
        // Set the threshold on this instance, NOT via the JVM-global system property: that is
        // read once at construction, and a forked JVM does not reliably observe a property set
        // inside @Setup, which silently leaves the INDEX arm re-measuring the scan. SCAN:
        // threshold above the largest n (index never engages). INDEX: threshold 2 (n >= 2).
        requestMatchers.withCandidateIndexThreshold("SCAN".equals(indexMode) ? Integer.MAX_VALUE : 2);
        for (int i = 0; i < n; i++) {
            requestMatchers.add(buildExpectation(i), API);
        }
        probe = buildProbe();
    }

    private Expectation buildExpectation(int i) {
        if ("REGEX_HEAVY".equals(shape)) {
            // every expectation is an un-bucketable regex path -> all fall through.
            return new Expectation(request().withMethod("GET").withPath("/regex/path-[a-z]+-" + i))
                .thenRespond(response().withBody("r" + i));
        }
        return new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
            .thenRespond(response().withBody("e" + i));
    }

    private HttpRequest buildProbe() {
        if ("HIT".equals(outcome)) {
            if ("REGEX_HEAVY".equals(shape)) {
                // matches the last registered regex (worst case: scanned last in insertion order)
                return request().withMethod("GET").withPath("/regex/path-zzz-" + (n - 1));
            }
            // matches a literal in the middle of the set
            return request().withMethod("GET").withPath("/exact/path-" + (n / 2));
        }
        // MISS: matches nothing -> full scan worst case
        return request().withMethod("GET").withPath("/no-such-path-zzzzzz");
    }

    @Benchmark
    public Expectation firstMatchingExpectation() {
        return requestMatchers.firstMatchingExpectation(probe);
    }
}
