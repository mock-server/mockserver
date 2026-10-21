package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.matchers.DateTimeComparisonMatcher.Operator;

import java.lang.management.ManagementFactory;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;

/**
 * An expectation with a date-time header matcher is compared against every header value of a request, so
 * a value that is not a date-time must be rejected without creating an exception. An exception records the
 * whole stack, so at the depth of a Netty worker it allocates several kilobytes; the shape check allocates
 * a few hundred bytes. Measured deep in the stack so the two differ by an order of magnitude.
 */
public class DateTimeComparisonMatcherCostTest {

    private static final String[] UNRELATED_HEADER_VALUES = {
        "application/json", "*/*", "keep-alive", "gzip, deflate, br", "no-cache",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", "en-GB,en;q=0.9", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x",
        "1.0 proxy", "2026-10-21 15:00", "Wed 21 Oct 2026", "localhost:1080"
    };
    private static final int STACK_DEPTH = 400;
    private static final int ROUNDS = 50;
    private static final Instant NOW = Instant.parse("2026-10-21T15:04:05Z");

    @Test
    public void shouldRejectValuesThatAreNotDateTimesWithoutAnException() {
        for (String format : new String[]{null, "iso", "rfc1123", "dd/MM/yyyy HH:mm"}) {
            DateTimeComparisonMatcher matcher = DateTimeComparisonMatcher.compile(Operator.AFTER, "now -1 days", format, null, null, null, null);
            for (String value : UNRELATED_HEADER_VALUES) {
                assertThat(format + " " + value, matcher.compare(value, NOW), is(nullValue()));
            }
            long bytesPerComparison = atDepth(STACK_DEPTH, () -> allocatedPerComparison(matcher));
            assertThat("bytes allocated per comparison, actualFormat " + format, bytesPerComparison, lessThan(2048L));
        }
    }

    private static long allocatedPerComparison(DateTimeComparisonMatcher matcher) {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(thread);
        for (int round = 0; round < ROUNDS; round++) {
            for (String value : UNRELATED_HEADER_VALUES) {
                matcher.compare(value, NOW);
            }
        }
        return (threads.getThreadAllocatedBytes(thread) - before) / ((long) ROUNDS * UNRELATED_HEADER_VALUES.length);
    }

    private interface Measurement {
        long run();
    }

    private static long atDepth(int depth, Measurement measurement) {
        return depth == 0 ? measurement.run() : atDepth(depth - 1, measurement);
    }
}
