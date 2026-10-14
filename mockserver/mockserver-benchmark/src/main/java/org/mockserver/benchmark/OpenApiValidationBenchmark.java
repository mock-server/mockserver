package org.mockserver.benchmark;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;
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

/**
 * Hot-path micro-benchmark for the OpenAPI request/response body-validation step.
 *
 * <p>When {@code validateRequestsAgainstOpenApiSpec} / response validation is enabled, building a
 * {@link JsonSchemaValidator} per request would compile a networknt {@code Schema} and a fresh
 * {@code SchemaRegistry} every time, even though the operation schema never changes. This benchmark
 * isolates that compilation cost from the constant validate-only cost, showing what the
 * content-keyed validator cache the validators use saves.
 *
 * <p>The two arms validate the <em>same</em> payload against the <em>same</em> schema:
 * <ul>
 *   <li>{@code PER_REQUEST_COMPILE} — {@code new JsonSchemaValidator(...)} then {@code isValid}
 *       (uncached: compile Schema+SchemaRegistry every request);</li>
 *   <li>{@code CACHED} — {@link JsonSchemaValidator#cachedJsonSchemaValidator} then {@code isValid}
 *       (compile once, reuse the validator).</li>
 * </ul>
 * The {@code schemaComplexity} param toggles a small flat schema vs a larger nested/{@code $ref}-style
 * (inlined, as swagger-parser would resolve it) schema; the compile cost — and therefore the win —
 * grows with schema size. Run with the GC profiler for bytes-allocated-per-op:
 *
 * <pre>./run.sh -prof gc OpenApiValidationBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OpenApiValidationBenchmark {

    /** Whether each op rebuilds the validator or reuses the cached one. */
    @Param({"PER_REQUEST_COMPILE", "CACHED"})
    public String mode;

    /** Schema size — the per-request compile cost (and the cache win) grows with this. */
    @Param({"SMALL", "LARGE"})
    public String schemaComplexity;

    private final MockServerLogger logger = new MockServerLogger();
    private String schemaJson;
    private String validPayload;

    @Setup(Level.Trial)
    public void setup() {
        if ("SMALL".equals(schemaComplexity)) {
            schemaJson = SMALL_SCHEMA;
            validPayload = SMALL_VALID_PAYLOAD;
        } else {
            schemaJson = LARGE_SCHEMA;
            validPayload = LARGE_VALID_PAYLOAD;
        }
        // warm the cache for the CACHED arm so the measured ops only pay the lookup + validate cost
        JsonSchemaValidator.cachedJsonSchemaValidator(logger, schemaJson);
    }

    @Benchmark
    public String validate() {
        JsonSchemaValidator validator;
        if ("CACHED".equals(mode)) {
            validator = JsonSchemaValidator.cachedJsonSchemaValidator(logger, schemaJson);
        } else {
            validator = new JsonSchemaValidator(logger, schemaJson);
        }
        // false == do not append the OpenAPI-spec help footer, matching the OpenAPI validators
        return validator.isValid(validPayload, false);
    }

    // ---- small, flat schema -------------------------------------------------------------------

    private static final String SMALL_SCHEMA =
        "{\"type\":\"object\"," +
            "\"properties\":{" +
            "\"id\":{\"type\":\"integer\"}," +
            "\"name\":{\"type\":\"string\"}" +
            "}," +
            "\"required\":[\"id\",\"name\"]," +
            "\"additionalProperties\":false}";

    private static final String SMALL_VALID_PAYLOAD = "{\"id\": 1, \"name\": \"Fido\"}";

    // ---- larger, nested schema (as swagger-parser resolveFully would inline a $ref-heavy spec) --

    private static final String LARGE_SCHEMA =
        "{\"type\":\"object\"," +
            "\"required\":[\"id\",\"name\",\"address\",\"items\"]," +
            "\"additionalProperties\":false," +
            "\"properties\":{" +
            "\"id\":{\"type\":\"integer\",\"minimum\":1}," +
            "\"name\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":128}," +
            "\"email\":{\"type\":\"string\",\"format\":\"email\"}," +
            "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":16}," +
            "\"status\":{\"type\":\"string\",\"enum\":[\"new\",\"active\",\"closed\"]}," +
            "\"address\":{\"type\":\"object\"," +
            "\"required\":[\"street\",\"city\",\"country\"]," +
            "\"additionalProperties\":false," +
            "\"properties\":{" +
            "\"street\":{\"type\":\"string\"}," +
            "\"city\":{\"type\":\"string\"}," +
            "\"region\":{\"type\":\"string\"}," +
            "\"postcode\":{\"type\":\"string\",\"pattern\":\"^[A-Za-z0-9 -]{3,10}$\"}," +
            "\"country\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":2}" +
            "}}," +
            "\"items\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":100," +
            "\"items\":{\"type\":\"object\"," +
            "\"required\":[\"sku\",\"quantity\",\"unitPrice\"]," +
            "\"additionalProperties\":false," +
            "\"properties\":{" +
            "\"sku\":{\"type\":\"string\",\"pattern\":\"^[A-Z]{3}-[0-9]{3}$\"}," +
            "\"description\":{\"type\":\"string\"}," +
            "\"quantity\":{\"type\":\"integer\",\"minimum\":1}," +
            "\"unitPrice\":{\"type\":\"number\",\"minimum\":0}," +
            "\"dimensions\":{\"type\":\"object\"," +
            "\"additionalProperties\":false," +
            "\"properties\":{" +
            "\"length\":{\"type\":\"number\"}," +
            "\"width\":{\"type\":\"number\"}," +
            "\"height\":{\"type\":\"number\"}" +
            "}}" +
            "}}}," +
            "\"payment\":{\"type\":\"object\"," +
            "\"additionalProperties\":false," +
            "\"properties\":{" +
            "\"method\":{\"type\":\"string\",\"enum\":[\"card\",\"invoice\",\"paypal\"]}," +
            "\"terms\":{\"type\":\"string\"}," +
            "\"currency\":{\"type\":\"string\",\"minLength\":3,\"maxLength\":3}" +
            "}}" +
            "}}";

    private static final String LARGE_VALID_PAYLOAD =
        "{\"id\":42,\"name\":\"Acme Corporation\",\"email\":\"orders@acme.example.com\"," +
            "\"tags\":[\"priority\",\"wholesale\"],\"status\":\"active\"," +
            "\"address\":{\"street\":\"123 Industrial Way\",\"city\":\"Springfield\"," +
            "\"region\":\"IL\",\"postcode\":\"62704\",\"country\":\"US\"}," +
            "\"items\":[" +
            "{\"sku\":\"AAA-111\",\"description\":\"Widget, large, blue\",\"quantity\":10,\"unitPrice\":19.99," +
            "\"dimensions\":{\"length\":10.0,\"width\":5.0,\"height\":2.5}}," +
            "{\"sku\":\"BBB-222\",\"description\":\"Gadget, small, red\",\"quantity\":5,\"unitPrice\":49.50}," +
            "{\"sku\":\"CCC-333\",\"description\":\"Sprocket assembly\",\"quantity\":2,\"unitPrice\":129.00}" +
            "]," +
            "\"payment\":{\"method\":\"invoice\",\"terms\":\"net30\",\"currency\":\"USD\"}}";
}
