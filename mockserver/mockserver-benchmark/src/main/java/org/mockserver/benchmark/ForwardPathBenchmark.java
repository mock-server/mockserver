package org.mockserver.benchmark;

import org.mockserver.configuration.Configuration;
import org.mockserver.load.IterationContext;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockserver.model.HttpRequest.request;

/**
 * Hot-path micro-benchmark for MockServer's <em>outbound / forward</em> load path — the per-iteration
 * work {@code LoadScenarioOrchestrator.RunningScenario.render(step, iteration)} performs before each
 * request is handed to the Netty HTTP client.
 *
 * <p>The render path (see {@code LoadScenarioOrchestrator}) is, per dispatched request:
 * <ol>
 *   <li>{@code request.clone()} — a deep copy of the step's request template;</li>
 *   <li>{@code TemplateEngine.renderTemplate(path, request, iteration)} when the path contains a
 *       template marker;</li>
 *   <li>{@code TemplateEngine.renderTemplate(body, request, iteration)} when the body contains a
 *       template marker.</li>
 * </ol>
 *
 * <p>The Velocity engine instance is cached for the life of the scenario. Each {@code renderTemplate}
 * call still allocates a fresh {@code StringWriter}, {@code VelocityContext}, the built-in
 * function/helper maps, and a {@code RequestBodyExtractionHelper} (see
 * {@code VelocityTemplateEngine.renderTemplate}); the parsed template AST itself is now cached and
 * rendered via Velocity's own {@code Template.merge(...)} (parse once, render many) rather than
 * re-parsed on every call. This benchmark isolates those per-iteration costs deterministically and
 * network-free, so the allocation/CPU hotspots can be ranked without the noise of the end-to-end k6
 * signal.
 *
 * <p>Mirrors {@link MatchingBenchmark}'s structure. Run the allocation profiler — the
 * {@code gc.alloc.rate.norm} (bytes/op) column is the headline number:
 *
 * <pre>./run.sh -prof gc ForwardPathBenchmark</pre>
 *
 * <p>Uses the default {@link Configuration} (the common case). Velocity is the default load-step
 * engine.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ForwardPathBenchmark {

    private TemplateEngine velocity;

    /** A monotonically advancing iteration counter, as the orchestrator supplies. */
    private final AtomicLong globalIndex = new AtomicLong();

    // --- template strings (parsed once then served from the engine's AST cache, as in production) ---

    /** SIMPLE: a single ${...} reference — the cheapest realistic templated field. */
    private static final String SIMPLE_TEMPLATE = "/item/$iteration.index";

    /** MEDIUM: several references mixing request fields and the iteration variable. */
    private static final String MEDIUM_TEMPLATE =
        "{'id': '$iteration.index', 'vu': '$iteration.vuId', 'count': '$iteration.count', " +
            "'method': '$request.method', 'path': '$request.path'}";

    /** COMPLEX: many references including a uuid and a faker call (the expensive generators). */
    private static final String COMPLEX_TEMPLATE =
        "{'id': '$iteration.index', 'vu': '$iteration.vuId', 'count': '$iteration.count', " +
            "'elapsed': '$iteration.elapsedMillis', 'method': '$request.method', " +
            "'path': '$request.path', 'uuid': '$uuid', 'uuid2': '$uuid', " +
            "'firstName': '$faker.name().firstName()', 'lastName': '$faker.name().lastName()', " +
            "'email': '$faker.internet().emailAddress()', 'now': '$now', 'rand': '$rand_int_100'}";

    // --- request contexts ---

    /** The request context used to render templates (small, mirrors a typical load step request). */
    private HttpRequest renderContext;

    /** A small request to clone (a couple of headers, short body). */
    private HttpRequest smallRequest;

    /** A large request to clone (many headers + a sizeable body). */
    private HttpRequest largeRequest;

    @Setup
    public void setup() {
        velocity = new VelocityTemplateEngine(new MockServerLogger(), Configuration.configuration());

        renderContext = request()
            .withMethod("POST")
            .withPath("/api/orders")
            .withHeader("Host", "upstream.local:1090")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"sku\":\"ABC-123\",\"qty\":2}");

        smallRequest = request()
            .withMethod("POST")
            .withPath("/api/orders/$iteration.index")
            .withHeader("Host", "upstream.local:1090")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"sku\":\"ABC-123\",\"qty\":2}");

        // Large request: 30 headers + ~8KB body — the per-iteration clone cost when a step carries
        // realistic headers and a non-trivial payload.
        HttpRequest large = request()
            .withMethod("POST")
            .withPath("/api/v2/customers/profile/enrich");
        for (int i = 0; i < 30; i++) {
            large.withHeader(new Header("X-Custom-Header-" + i, "value-" + i + "-" + "x".repeat(20)));
        }
        StringBuilder body = new StringBuilder(8192);
        body.append("{\"records\":[");
        for (int i = 0; i < 200; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"id\":").append(i).append(",\"name\":\"record-").append(i)
                .append("\",\"payload\":\"").append("y".repeat(20)).append("\"}");
        }
        body.append("]}");
        large.withBody(body.toString());
        largeRequest = large;
    }

    private IterationContext nextIteration() {
        long idx = globalIndex.getAndIncrement();
        return new IterationContext(idx, (int) (idx % 50), idx / 50, idx * 3, idx);
    }

    // ----- 1. Template render (SIMPLE / MEDIUM / COMPLEX) -----

    @Benchmark
    public void renderSimple(Blackhole bh) {
        bh.consume(velocity.renderTemplate(SIMPLE_TEMPLATE, renderContext, nextIteration()));
    }

    @Benchmark
    public void renderMedium(Blackhole bh) {
        bh.consume(velocity.renderTemplate(MEDIUM_TEMPLATE, renderContext, nextIteration()));
    }

    @Benchmark
    public void renderComplex(Blackhole bh) {
        bh.consume(velocity.renderTemplate(COMPLEX_TEMPLATE, renderContext, nextIteration()));
    }

    // ----- 2. Request clone (small / large) -----

    @Benchmark
    public void cloneSmall(Blackhole bh) {
        bh.consume(smallRequest.clone());
    }

    @Benchmark
    public void cloneLarge(Blackhole bh) {
        bh.consume(largeRequest.clone());
    }

    // ----- 3. Combined render path: clone + path render + body render -----
    // Reconstructs RunningScenario.render(step, iteration): clone the request, then render the path
    // and body templates against the ORIGINAL request context (as the orchestrator does), writing
    // the rendered values back onto the clone.

    @Benchmark
    public void renderPathSmall(Blackhole bh) {
        IterationContext iteration = nextIteration();
        HttpRequest clone = smallRequest.clone();
        // path contains "$iteration.index" -> rendered against the original request context
        clone.withPath(velocity.renderTemplate(smallRequest.getPath().getValue(), smallRequest, iteration));
        bh.consume(clone);
    }

    @Benchmark
    public void renderPathAndBodyMedium(Blackhole bh) {
        IterationContext iteration = nextIteration();
        HttpRequest clone = renderContext.clone();
        clone.withPath(velocity.renderTemplate(SIMPLE_TEMPLATE, renderContext, iteration));
        clone.withBody(velocity.renderTemplate(MEDIUM_TEMPLATE, renderContext, iteration));
        bh.consume(clone);
    }

    @Benchmark
    public void renderPathAndBodyComplex(Blackhole bh) {
        IterationContext iteration = nextIteration();
        HttpRequest clone = renderContext.clone();
        clone.withPath(velocity.renderTemplate(SIMPLE_TEMPLATE, renderContext, iteration));
        clone.withBody(velocity.renderTemplate(COMPLEX_TEMPLATE, renderContext, iteration));
        bh.consume(clone);
    }
}
