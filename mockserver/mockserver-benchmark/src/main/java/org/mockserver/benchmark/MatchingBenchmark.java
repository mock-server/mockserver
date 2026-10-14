package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.model.JsonPathBody;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.model.XPathBody;
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
 * Hot-path micro-benchmark for {@link RequestMatchers#firstMatchingExpectation}.
 *
 * <p>The benchmarked request matches <em>no</em> expectation, which forces the
 * full N-matcher scan — the worst case for per-request allocation (a
 * {@code MatchDifference} allocated per matcher, the sorted matcher list, the
 * {@code becauseBuilder} strings). Run with the GC profiler to capture
 * bytes-allocated-per-op:
 *
 * <pre>./run.sh -prof gc MatchingBenchmark</pre>
 *
 * <p>Metrics are off; {@code detailedMatchFailures} and the log level are
 * parameterised; the {@code true} arm (the shipped default) exercises the
 * {@code MatchDifference} formatting path. A reduction in {@code gc.alloc.rate.norm}
 * here is the proof an allocation win is real (k6's end-to-end signal cannot
 * isolate it).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MatchingBenchmark {

    /** Number of expectations registered before matching (scan length). */
    @Param({"1", "10", "100", "1000"})
    public int expectationCount;

    /**
     * Shape of the registered matchers (exercises different matcher code).
     *
     * <ul>
     *   <li>{@code EXACT}/{@code REGEX} — fail fast on PATH; never reach body/headers.</li>
     *   <li>{@code JSON_BODY} — a JSON body matcher against a JSON request body.</li>
     *   <li>{@code HEADERS_MISS} — every expectation shares method+path (so the scan passes
     *       method+path and reaches HEADERS) but carries a distinct header matcher; the request
     *       has the same method+path plus several headers, missing on every expectation's header
     *       matcher. Forces the request-side header map to be (re)built for ALL N expectations —
     *       the path request-side map memoization serves.</li>
     *   <li>{@code XML_BODY} — every expectation carries a JSON-schema body matcher; the request
     *       carries an {@code application/xml} body, so {@code bodyMatches} routes through
     *       {@code JsonSchemaBodyDecoder.convertToJson}'s XML branch (XML DOM parse + ObjectMapper
     *       serialisation) — the path the per-request XML→JSON cache serves. BODY is matched before
     *       HEADERS in the field order, so a blank
     *       method/path on each expectation reaches the body matcher for ALL N expectations.</li>
     *   <li>{@code JSON_DEEP_DEFER} / {@code JSON_DEEP_REJECT} — every expectation carries a
     *       {@code JsonBody} (default {@code ONLY_MATCHING_FIELDS}) that is a nested object
     *       <em>containing an array of objects</em>, so {@code JsonStringMatcher} routes through the
     *       json-unit {@code ComparisonMatrix} the flat {@code JSON_BODY} arm never constructs — the
     *       exact shape the structural pre-filter targets. In {@code JSON_DEEP_DEFER} the request
     *       carries all the expected object keys (so the pure-negative pre-filter CANNOT prove a
     *       non-match and defers to the full diff — measuring the pre-filter's fixed overhead), yet a
     *       differing scalar makes the diff report not-similar so the scan still visits all N. In
     *       {@code JSON_DEEP_REJECT} the request is missing a nested expected key, so the pre-filter
     *       fast-rejects before the matrix is built — measuring the win. BODY precedes HEADERS in the
     *       field order and the expectations carry blank method/path, so the body matcher is reached
     *       for ALL N.</li>
     *   <li>{@code JSON_PATH} / {@code JSON_SCHEMA} — every expectation carries a JSONPath (resp.
     *       JSON-schema) body matcher that the ~1KB JSON request body never satisfies, so the body is
     *       handed to the matcher once per candidate expectation — measuring what each candidate pays
     *       to parse the same request body.</li>
     *   <li>{@code XPATH} — every expectation carries an XPath body matcher that the ~1KB XML request
     *       body never satisfies; measures the per-candidate DOM parse and XPath evaluation.</li>
     * </ul>
     */
    @Param({"EXACT", "REGEX", "JSON_BODY", "HEADERS_MISS", "XML_BODY", "JSON_DEEP_DEFER", "JSON_DEEP_REJECT", "JSON_PATH", "XPATH", "JSON_SCHEMA"})
    public String matcherType;

    /**
     * Server log level. INFO is the default; WARN models a performance-tuned
     * deployment (logging reduced below INFO). The per-field "because" string on
     * the match path is built only when INFO is enabled, so this param shows what
     * that gate saves.
     */
    @Param({"INFO", "WARN"})
    public String logLevel;

    /**
     * Whether detailed match-failure reports are recorded. {@code true} is the
     * shipped default and turns on the {@code MatchDifference.addDifference} ->
     * {@code StringFormatter} path, the largest production allocation source. This
     * param exists so both arms are measurable and the detailed arm can be gated
     * with its own absolute allocation floor, so a regression that re-introduces
     * eager formatting fails the per-merge gate
     * ({@code .buildkite/scripts/steps/perf-alloc-gate.sh}).
     *
     * <p>Consumers that measure a single workload MUST pin this param: the daily
     * micro-bench primary run and the doc-site scaling sweep pin it {@code false}
     * (their baselines describe the non-detailed shape, and an unpinned expansion
     * would collide their result keys); the allocation gate pins BOTH values so it
     * measures and floors each arm separately.
     */
    @Param({"false", "true"})
    public boolean detailedMatchFailures;

    private RequestMatchers requestMatchers;
    private HttpRequest noMatchRequest;

    @Setup(Level.Trial)
    public void setup() {
        // Configuration.configuration() MUST be constructed AFTER these statics are
        // set: it snapshots ConfigurationProperties (detailedMatchFailures() falls
        // back to the static when its own field is null), so the trial's param
        // values must already be in place.
        ConfigurationProperties.logLevel(logLevel);
        ConfigurationProperties.detailedMatchFailures(detailedMatchFailures);
        Configuration configuration = Configuration.configuration();
        requestMatchers = new RequestMatchers(
            configuration,
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
        for (int i = 0; i < expectationCount; i++) {
            requestMatchers.add(buildExpectation(i), API);
        }
        noMatchRequest = buildNoMatchRequest();
    }

    /**
     * Builds the benchmarked request — it matches <em>no</em> registered expectation, forcing the
     * full N-matcher scan, but is shaped so that for HEADERS_MISS / XML_BODY the scan actually
     * reaches the header / body matcher of every expectation (rather than failing fast on PATH).
     */
    private HttpRequest buildNoMatchRequest() {
        switch (matcherType) {
            case "HEADERS_MISS": {
                // same method+path as every expectation -> passes method+path, reaches HEADERS for
                // all N; carries several headers incl. an X-Tenant that matches no expectation.
                HttpRequest r = request()
                    .withMethod("GET")
                    .withPath("/headers/scan");
                r.withHeader(new Header("X-Tenant", "tenant-none-zzzzzz"));
                r.withHeader(new Header("Accept", "application/json"));
                r.withHeader(new Header("Accept-Encoding", "gzip, deflate, br"));
                r.withHeader(new Header("User-Agent", "benchmark-client/1.0"));
                r.withHeader(new Header("Authorization", "Bearer abcdef0123456789"));
                r.withHeader(new Header("X-Request-Id", "11111111-2222-3333-4444-555555555555"));
                r.withHeader(new Header("X-Forwarded-For", "203.0.113.7"));
                r.withHeader(new Header("Cache-Control", "no-cache"));
                r.withHeader(new Header("Connection", "keep-alive"));
                r.withHeader(new Header("Content-Type", "application/json"));
                return r;
            }
            case "XPATH":
            case "XML_BODY": {
                // an application/xml body matched against JSON-schema body matchers -> bodyMatches
                // routes through convertToJson's XML branch (DOM parse + ObjectMapper). Body is
                // matched before headers in the field order, so each expectation
                // (blank method/path) reaches the body matcher; the converted JSON fails the schema.
                HttpRequest r = request()
                    .withBody(XML_BODY);
                r.withHeader(new Header("Content-Type", "application/xml"));
                return r;
            }
            case "JSON_DEEP_DEFER":
                // carries EVERY expected object key (order.id, order.customer.{name,tier}, order.items),
                // so the structural pre-filter cannot prove a non-match and defers to json-unit, which
                // builds the ComparisonMatrix over the items array and finds order.id != any expected id
                // -> not similar, scan continues. Measures the pre-filter's fixed defer overhead.
                return request().withBody(new JsonBody(JSON_DEEP_DEFER_BODY, JsonBody.DEFAULT_MATCH_TYPE));
            case "JSON_DEEP_REJECT":
                // missing the nested order.customer.tier key every expectation requires, so the
                // pre-filter fast-rejects before json-unit builds the ComparisonMatrix. Measures the win.
                return request().withBody(new JsonBody(JSON_DEEP_REJECT_BODY, JsonBody.DEFAULT_MATCH_TYPE));
            case "JSON_PATH":
            case "JSON_SCHEMA":
                // a ~1KB JSON document that satisfies no expectation's JSONPath / JSON schema
                return request().withBody(new JsonBody(JSON_ORDER_BODY, JsonBody.DEFAULT_MATCH_TYPE));
            default:
                // matches none of the registered expectations -> full scan of all N
                return request()
                    .withMethod("GET")
                    .withPath("/no-such-path-zzzzzz")
                    .withBody("{\"unmatched\":true}");
        }
    }

    private Expectation buildExpectation(int i) {
        switch (matcherType) {
            case "REGEX":
                return new Expectation(request().withPath("/regex/path-[a-z]+-" + i))
                    .thenRespond(response().withBody("r" + i));
            case "JSON_BODY":
                return new Expectation(request().withBody(new JsonBody("{\"id\": " + i + "}")))
                    .thenRespond(response().withBody("j" + i));
            case "HEADERS_MISS":
                // shared method+path so the scan reaches HEADERS; a distinct header matcher per
                // expectation that the request never satisfies (X-Tenant=tenant-{i})
                return new Expectation(
                    request()
                        .withMethod("GET")
                        .withPath("/headers/scan")
                        .withHeader(new Header("X-Tenant", "tenant-" + i))
                ).thenRespond(response().withBody("h" + i));
            case "XML_BODY":
                // blank method/path -> match-anything for those fields, so BODY is reached; a
                // JSON-schema body matcher forces convertToJson (XML branch) against the XML request
                return new Expectation(
                    request().withBody(JsonSchemaBody.jsonSchema(
                        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"minimum\":" + (1000000 + i) + "}},\"required\":[\"id\"]}"
                    ))
                ).thenRespond(response().withBody("x" + i));
            case "JSON_DEEP_DEFER":
            case "JSON_DEEP_REJECT":
                // blank method/path -> BODY is reached for all N; a nested-object-with-array-of-objects
                // JsonBody (default ONLY_MATCHING_FIELDS) whose distinct order.id per expectation means
                // no request ever matches, forcing the full scan through the json-unit path.
                return new Expectation(
                    request().withBody(new JsonBody(deepExpectedBody(i), JsonBody.DEFAULT_MATCH_TYPE))
                ).thenRespond(response().withBody("d" + i));
            case "JSON_PATH":
                // blank method/path -> BODY is reached for all N; the filter selects no item
                return new Expectation(
                    request().withBody(JsonPathBody.jsonPath("$.order.items[?(@.sku == 'SKU-" + i + "')]"))
                ).thenRespond(response().withBody("p" + i));
            case "XPATH":
                return new Expectation(
                    request().withBody(XPathBody.xpath("/order/items/item[@sku='SKU-" + i + "']"))
                ).thenRespond(response().withBody("x" + i));
            case "JSON_SCHEMA":
                return new Expectation(
                    request().withBody(JsonSchemaBody.jsonSchema(
                        "{\"type\":\"object\",\"properties\":{\"order\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"minimum\":" + (1000000 + i) + "}},\"required\":[\"id\"]}},\"required\":[\"order\"]}"
                    ))
                ).thenRespond(response().withBody("s" + i));
            case "EXACT":
            default:
                return new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
                    .thenRespond(response().withBody("e" + i));
        }
    }

    /**
     * The expected JSON body for the JSON_DEEP_* arms: a nested object CONTAINING AN ARRAY OF OBJECTS,
     * so matching routes through json-unit's {@code ComparisonMatrix}. Each expectation gets a distinct
     * {@code order.id} so no request matches (forcing the full scan).
     */
    private static String deepExpectedBody(int i) {
        return "{\"order\":{\"id\":" + i + ",\"customer\":{\"name\":\"Acme\",\"tier\":\"gold\"},"
            + "\"items\":[{\"sku\":\"AAA-111\",\"qty\":10,\"price\":19.99},"
            + "{\"sku\":\"BBB-222\",\"qty\":5,\"price\":49.50}]}}";
    }

    /**
     * DEFER request: carries every expected object key (order.id, order.customer.{name,tier},
     * order.items) so the structural pre-filter cannot prove a non-match and defers to json-unit; the
     * order.id (999999) matches no expectation so json-unit reports not-similar after building the matrix.
     */
    private static final String JSON_DEEP_DEFER_BODY =
        "{\"order\":{\"id\":999999,\"customer\":{\"name\":\"Acme\",\"tier\":\"gold\"},"
            + "\"items\":[{\"sku\":\"ZZZ-999\",\"qty\":0,\"price\":0.0}]}}";

    /**
     * REJECT request: structurally close but MISSING the nested order.customer.tier key every
     * expectation requires, so the pre-filter fast-rejects before json-unit builds the ComparisonMatrix.
     */
    private static final String JSON_DEEP_REJECT_BODY =
        "{\"order\":{\"id\":999999,\"customer\":{\"name\":\"Acme\"},"
            + "\"items\":[{\"sku\":\"ZZZ-999\",\"qty\":0,\"price\":0.0}]}}";

    /** The JSON counterpart of {@link #XML_BODY} (~1KB) for the JSON_PATH and JSON_SCHEMA arms. */
    private static final String JSON_ORDER_BODY =
        "{\"order\":{\"id\":42,"
            + "\"customer\":{\"name\":\"Acme Corporation\","
            + "\"contact\":{\"email\":\"orders@acme.example.com\",\"phone\":\"+1-555-0100\"},"
            + "\"address\":{\"street\":\"123 Industrial Way\",\"city\":\"Springfield\",\"region\":\"IL\","
            + "\"postcode\":\"62704\",\"country\":\"US\"}},"
            + "\"items\":["
            + "{\"sku\":\"AAA-111\",\"description\":\"Widget, large, blue\",\"quantity\":10,\"unitPrice\":19.99},"
            + "{\"sku\":\"BBB-222\",\"description\":\"Gadget, small, red\",\"quantity\":5,\"unitPrice\":49.50},"
            + "{\"sku\":\"CCC-333\",\"description\":\"Sprocket assembly, stainless\",\"quantity\":2,\"unitPrice\":129.00}],"
            + "\"payment\":{\"method\":\"invoice\",\"terms\":\"net30\",\"currency\":\"USD\"},"
            + "\"notes\":\"Please deliver to the loading dock at the rear of the building before noon.\"}}";

    /** A non-trivial (~1KB), nested XML document so the DOM parse cost is visible per conversion. */
    private static final String XML_BODY =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<order id=\"42\">\n" +
        "  <customer>\n" +
        "    <name>Acme Corporation</name>\n" +
        "    <contact>\n" +
        "      <email>orders@acme.example.com</email>\n" +
        "      <phone>+1-555-0100</phone>\n" +
        "    </contact>\n" +
        "    <address>\n" +
        "      <street>123 Industrial Way</street>\n" +
        "      <city>Springfield</city>\n" +
        "      <region>IL</region>\n" +
        "      <postcode>62704</postcode>\n" +
        "      <country>US</country>\n" +
        "    </address>\n" +
        "  </customer>\n" +
        "  <items>\n" +
        "    <item sku=\"AAA-111\">\n" +
        "      <description>Widget, large, blue</description>\n" +
        "      <quantity>10</quantity>\n" +
        "      <unitPrice>19.99</unitPrice>\n" +
        "    </item>\n" +
        "    <item sku=\"BBB-222\">\n" +
        "      <description>Gadget, small, red</description>\n" +
        "      <quantity>5</quantity>\n" +
        "      <unitPrice>49.50</unitPrice>\n" +
        "    </item>\n" +
        "    <item sku=\"CCC-333\">\n" +
        "      <description>Sprocket assembly, stainless</description>\n" +
        "      <quantity>2</quantity>\n" +
        "      <unitPrice>129.00</unitPrice>\n" +
        "    </item>\n" +
        "  </items>\n" +
        "  <payment>\n" +
        "    <method>invoice</method>\n" +
        "    <terms>net30</terms>\n" +
        "    <currency>USD</currency>\n" +
        "  </payment>\n" +
        "  <notes>Please deliver to the loading dock at the rear of the building before noon.</notes>\n" +
        "</order>\n";

    @Benchmark
    public Expectation firstMatchingExpectation_noMatch() {
        // returns null (no match); returning it keeps JMH from eliminating the call
        return requestMatchers.firstMatchingExpectation(noMatchRequest);
    }
}
