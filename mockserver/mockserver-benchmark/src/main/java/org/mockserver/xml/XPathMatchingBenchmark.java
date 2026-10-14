package org.mockserver.xml;

import org.mockserver.configuration.ConfigurationProperties;
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

import javax.xml.xpath.XPathConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark for the XPath request-matching parse cost — the path
 * {@link XPathEvaluator#evaluateXPathExpression} exercises via {@code XPathMatcher} once per candidate
 * expectation during a match.
 *
 * <p>Placed in {@code org.mockserver.xml} (a split package with the core jar, as the sibling
 * {@code org.mockserver.matchers} / {@code org.mockserver.mock} proofs in this module already are) so
 * it can construct {@link XPathEvaluator} directly. This module is out of the reactor and resolves
 * {@code mockserver-core} from {@code ~/.m2} — {@code install} core first and confirm the installed
 * jar's classes match {@code target/classes} by sha1, so the numbers are for the code under test.
 *
 * <p><strong>What it models.</strong> One {@code @Benchmark} invocation is ONE request: a single body
 * matched against {@code candidateCount} distinct XPath expressions. Successive invocations rotate
 * over {@link #DISTINCT_BODIES} distinct bodies, so each "request" presents a body different from the
 * previous one — the realistic case where a per-thread parse cache is a fresh miss on the first
 * candidate of every request and a hit for that request's remaining candidates. The benchmark makes
 * NO reference to the cache API, so it compiles and runs unchanged against a core that re-parses
 * every candidate and one that parses once per request.
 *
 * <p><strong>What it does NOT capture.</strong>
 * <ul>
 *   <li>The full {@code RequestMatchers} scan (method/path/header short-circuits, {@code MatchDifference}
 *       allocation, matcher sorting) — it isolates the XPath parse+evaluate core only.</li>
 *   <li>Cross-request cache sharing — there is none by design; each request re-parses its own body once.</li>
 *   <li>Bounding the parse under the timeout. That is a worst-case / hostile-input property; this
 *       measures only the common-case allocation/time of the per-request parse cache. Both arms
 *       run the pool hand-off per evaluation, so that fixed cost cancels in the delta.</li>
 *   <li>Real-world body shapes and expression complexity — synthetic flat XML and existence predicates.</li>
 * </ul>
 *
 * <pre>./run.sh -prof gc XPathMatchingBenchmark</pre>
 * The headline columns are µs/op (time) and {@code gc.alloc.rate.norm} (B/op).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class XPathMatchingBenchmark {

    /** power of two so {@code index & (DISTINCT_BODIES - 1)} rotates without a modulo. */
    private static final int DISTINCT_BODIES = 16;

    /** Number of candidate XPath expressions the one request body is matched against. */
    @Param({"1", "20"})
    public int candidateCount;

    /** Body size: SMALL (~20 elements) vs LARGE (~2000 elements) so the parse cost is visible. */
    @Param({"SMALL", "LARGE"})
    public String bodySize;

    private final StringToXmlDocumentParser.ErrorLogger noop = (xmlAsString, exception, level) -> {
    };
    private String[] bodies;
    private int index;
    private List<XPathEvaluator> evaluators;

    @Setup(Level.Trial)
    public void setup() {
        // generous timeout so no evaluation times out during measurement
        ConfigurationProperties.xpathMatchingTimeoutMillis(60_000L);
        int nodes = "SMALL".equals(bodySize) ? 20 : 2_000;
        bodies = new String[DISTINCT_BODIES];
        for (int k = 0; k < DISTINCT_BODIES; k++) {
            bodies[k] = buildXml(nodes, k);
        }
        evaluators = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            // distinct existence predicate per candidate; each must parse+evaluate the whole body
            evaluators.add(new XPathEvaluator("/root/n[@id='" + i + "']", null));
        }
    }

    @Benchmark
    public int matchOneRequestAgainstAllCandidates() {
        String body = bodies[index++ & (DISTINCT_BODIES - 1)];
        int truthy = 0;
        for (XPathEvaluator evaluator : evaluators) {
            if (Boolean.TRUE.equals(evaluator.evaluateXPathExpression(body, noop, XPathConstants.BOOLEAN))) {
                truthy++;
            }
        }
        return truthy;
    }

    private static String buildXml(int nodes, int marker) {
        // the marker attribute makes every body a distinct string so consecutive requests do not
        // collide in a single-slot per-thread cache
        StringBuilder sb = new StringBuilder("<root req=\"").append(marker).append("\">");
        for (int i = 0; i < nodes; i++) {
            sb.append("<n id=\"").append(i).append("\">v").append(i).append("</n>");
        }
        sb.append("</root>");
        return sb.toString();
    }
}
