package org.mockserver.matchers;

import com.jayway.jsonpath.JsonPath;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.xml.XPathEvaluator;

import javax.xml.xpath.XPathConstants;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeC1Only;

/**
 * A JSONPath or XPath body matcher without a date-time comparison must cost exactly what it did before
 * the comparison existed: the same evaluation, and not one byte more, as calling the JSONPath or XPath
 * library directly.
 */
public class BodyDateTimeMatcherCostTest {

    private static final String JSON_PATH = "$.order.items[?(@.price > 5)].sku";
    private static final String JSON = "{\"order\": {\"id\": 7, \"created\": \"2026-10-21T15:00:00Z\", \"items\": [{\"sku\": \"a\", \"price\": 3}, {\"sku\": \"b\", \"price\": 8}]}}";
    private static final String XPATH = "/order/item[@price > 5]";
    private static final String XML = "<order id=\"7\"><created>2026-10-21T15:00:00Z</created><item price=\"3\">a</item><item price=\"8\">b</item></order>";
    private static final int OPERATIONS = 20_000;
    // the least of several interleaved measurements per arm: background work on a loaded JVM can only add bytes,
    // never remove the code's own, so the minimum is the code's cost and an injected allocation still fails
    private static final int ATTEMPTS = 5;
    private static final long TOLERANCE_BYTES = 8;
    private static volatile Object sink;

    // no XPath timeout, so the evaluation runs on this thread: the pool hand-off it otherwise makes allocates a
    // varying few bytes per call (a queue node, a wait node) depending on thread scheduling, in both arms alike
    private final MockServerLogger mockServerLogger = new MockServerLogger(Configuration.configuration().xpathMatchingTimeoutMillis(0L), BodyDateTimeMatcherCostTest.class);

    @Test
    public void shouldMatchJsonPathWithoutADateTimeAtTheCostOfTheJsonPathReadAlone() {
        JsonPathMatcher matcher = new JsonPathMatcher(mockServerLogger, JSON_PATH);
        JsonPath jsonPath = JsonPath.compile(JSON_PATH);

        assertSameCost(
            () -> matcher.matches(null, JSON),
            () -> {
                sink = jsonPath.read(JSON);
                return true;
            }
        );
    }

    @Test
    public void shouldMatchJsonPathWithoutADateTimeAgainstAParsedBodyAtTheCostOfTheJsonPathReadAlone() {
        JsonPathMatcher matcher = new JsonPathMatcher(mockServerLogger, JSON_PATH);
        JsonPath jsonPath = JsonPath.compile(JSON_PATH);
        ParsedBodyCache parsedBodyCache = ParsedBodyCache.forCurrentThread();

        assertSameCost(
            () -> matcher.matches(null, JSON, parsedBodyCache),
            () -> {
                com.jayway.jsonpath.Configuration configuration = com.jayway.jsonpath.Configuration.defaultConfiguration();
                sink = jsonPath.read(parsedBodyCache.jsonPathDocument(JSON, configuration.jsonProvider()), configuration);
                return true;
            }
        );
    }

    @Test
    public void shouldMatchXPathWithoutADateTimeAtTheCostOfTheXPathEvaluationAlone() {
        XPathMatcher matcher = new XPathMatcher(mockServerLogger, XPATH, null);
        XPathEvaluator evaluator = new XPathEvaluator(XPATH, null, mockServerLogger.getConfiguration());

        assertSameCost(
            () -> matcher.matches(null, XML),
            () -> {
                Object context = null;
                String matched = XML;
                // a capturing error logger, as the matcher's is
                return (Boolean) evaluator.evaluateXPathExpression(matched, (matchedInException, throwable, level) -> sink = context == null ? matched : context, XPathConstants.BOOLEAN);
            }
        );
    }

    private static void assertSameCost(BooleanSupplier matcher, BooleanSupplier library) {
        // C2 inlines the two arms differently, so escape analysis can remove an object in one and keep it in the other
        assumeC1Only("the matcher's cost against the library call alone");
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            perOperation(matcher);
            perOperation(library);
        }
        long withMatcher = Long.MAX_VALUE;
        long withLibrary = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            withMatcher = Math.min(withMatcher, perOperation(matcher));
            withLibrary = Math.min(withLibrary, perOperation(library));
        }
        assertThat("bytes per match through the matcher (" + withMatcher + ") beyond the library call alone (" + withLibrary + ")", withMatcher - withLibrary, lessThan(TOLERANCE_BYTES));
    }

    private static long perOperation(BooleanSupplier operation) {
        long before = allocatedBytes();
        for (int i = 0; i < OPERATIONS; i++) {
            if (!operation.getAsBoolean()) {
                throw new AssertionError("expected a match");
            }
        }
        long perOperation = (allocatedBytes() - before) / OPERATIONS;
        assertThat(perOperation > 0, is(true));
        return perOperation;
    }
}
