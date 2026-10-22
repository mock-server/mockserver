package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeHttpRequestIsNotInstrumented;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.matchers.HttpRequestPropertiesMatcherClientIpTest.received;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableString.string;

/**
 * A client IP matcher runs for every request an expectation that declares one is evaluated against, so it must
 * compare the address bytes the transport already captured, with no parsing and no allocation; and an expectation
 * without one must cost exactly what it did before the field existed.
 */
public class ClientIpMatcherCostTest {

    private static final int OPERATIONS = 200_000;
    // the least of several measurements: background work on a loaded JVM can only add bytes, never remove the
    // code's own, so the minimum is the code's cost and an injected allocation still fails every attempt
    private static final int ATTEMPTS = 3;
    // consumed so the compiler cannot discard the comparisons being measured
    private static volatile int sink;
    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(ClientIpMatcherCostTest.class);

    @Test
    public void shouldCompareClientAddressesWithoutAllocating() throws Exception {
        HttpRequest[] requests = {received("/api", "10.1.2.3"), received("/api", "192.168.1.1"), received("/api", "2001:db8::7"), received("/api", "::1")};
        for (String range : new String[]{"10.0.0.0/8", "!10.0.0.0/8", "172.16.0.0/12", "2001:db8::/32", "::/0", "192.168.1.1"}) {
            ClientIpMatcher matcher = ClientIpMatcher.compile(mockServerLogger, string(range));
            run(matcher, requests, OPERATIONS);
            long least = Long.MAX_VALUE;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long before = allocatedBytes();
                run(matcher, requests, OPERATIONS);
                least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
            }
            assumeHttpRequestIsNotInstrumented();
            assertThat("bytes allocated per comparison against " + range, least, is(0L));
        }
    }

    @Test
    public void shouldAddNoAllocationToMatchingAnExpectation() throws Exception {
        HttpRequestPropertiesMatcher withoutClientIp = matcher(request().withMethod("GET").withPath("/api"));
        HttpRequestPropertiesMatcher withClientIp = matcher(request().withMethod("GET").withPath("/api").withClientIp("10.0.0.0/8"));
        HttpRequest request = received("/api", "10.1.2.3");
        MatchDifference context = new MatchDifference(false, request).suppressMatchResultLogging();
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            perMatch(withoutClientIp, context, request);
            perMatch(withClientIp, context, request);
        }
        long without = perMatch(withoutClientIp, context, request);
        long with = perMatch(withClientIp, context, request);
        assumeHttpRequestIsNotInstrumented();
        assertThat("bytes per match with a client IP (" + with + ") beyond one without (" + without + ")", Math.abs(with - without), lessThan(8L));
    }

    private HttpRequestPropertiesMatcher matcher(HttpRequest expectationRequest) {
        HttpRequestPropertiesMatcher matcher = new HttpRequestPropertiesMatcher(configuration, mockServerLogger);
        matcher.update(new Expectation(expectationRequest));
        return matcher;
    }

    private static long perMatch(HttpRequestPropertiesMatcher matcher, MatchDifference context, HttpRequest request) {
        long least = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long before = allocatedBytes();
            for (int i = 0; i < OPERATIONS; i++) {
                if (!matcher.matches(context, request)) {
                    throw new AssertionError("expected a match");
                }
            }
            least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
        }
        return least;
    }

    private static void run(ClientIpMatcher matcher, HttpRequest[] requests, int operations) {
        int matched = 0;
        for (int i = 0; i < operations; i++) {
            if (matcher.matches(null, requests[i & 3])) {
                matched++;
            }
        }
        sink = matched;
    }

}
