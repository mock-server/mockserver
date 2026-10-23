package org.mockserver.authentication;

import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeHttpRequestIsNotInstrumented;

/**
 * Every log entry, mocked traffic included, passes its requests through {@link CredentialRedaction}, so deciding
 * that a request is not a control-plane one must allocate nothing, and a log entry for a data-plane request must
 * cost no more than one that carries no request argument at all.
 */
public class CredentialRedactionCostTest {

    private static final int OPERATIONS = 200_000;
    // the least of several measurements: background work on a loaded JVM can only add bytes, never remove the
    // code's own, so the minimum is the code's cost and an injected allocation still fails every attempt
    private static final int ATTEMPTS = 3;
    // consumed so the compiler cannot discard the checks being measured
    private static volatile Object sink;

    // a path no route opens with, paths that open like a route, a route path with a method it is not served on, and
    // a named-route prefix with a method it is not served on: each reaches a different depth of the check
    private static final HttpRequest[] DATA_PLANE = {
        request("/api/orders").withMethod("GET"),
        request("/products/1").withMethod("GET"),
        request("/mockserverish").withMethod("PUT"),
        request("/expectation").withMethod("GET"),
        request("/scenario/checkout").withMethod("POST"),
    };

    @Test
    public void shouldDecideADataPlaneRequestIsNotTheControlPlaneWithoutAllocating() {
        for (HttpRequest request : DATA_PLANE) {
            assertThat(request.getPath().getValue(), CredentialRedaction.forLog(request), sameInstance(request));
            assertThat(request.getPath().getValue(), CredentialRedaction.argumentForLog(request), sameInstance(request));
            check(request, OPERATIONS);
            long least = Long.MAX_VALUE;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                long before = allocatedBytes();
                check(request, OPERATIONS);
                least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
            }
            assumeHttpRequestIsNotInstrumented();
            assertThat("bytes allocated per check of " + request.getMethod().getValue() + " " + request.getPath().getValue(), least, is(0L));
        }
    }

    @Test
    public void shouldAddNoAllocationToALogEntryForADataPlaneRequest() {
        HttpRequest request = DATA_PLANE[0];
        LogEntry entry = new LogEntry();
        Object[] withRequest = {request, "because"};
        Object[] withoutRequest = {"request", "because"};
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            perEntry(entry, request, withRequest);
            perEntry(entry, request, withoutRequest);
        }
        long without = perEntry(entry, request, withoutRequest);
        long with = perEntry(entry, request, withRequest);
        assumeHttpRequestIsNotInstrumented();
        assertThat(entry.getHttpRequests()[0], sameInstance(request));
        assertThat("bytes per log entry with the request as an argument (" + with + ") beyond one without (" + without + ")", Math.abs(with - without), lessThan(8L));
    }

    private static long perEntry(LogEntry entry, HttpRequest request, Object[] arguments) {
        long least = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long before = allocatedBytes();
            for (int i = 0; i < OPERATIONS; i++) {
                entry.setHttpRequest(request).setArguments(arguments);
            }
            least = Math.min(least, (allocatedBytes() - before) / OPERATIONS);
        }
        sink = entry;
        return least;
    }

    private static void check(HttpRequest request, int operations) {
        Object last = null;
        for (int i = 0; i < operations; i++) {
            last = CredentialRedaction.forLog(request);
            last = CredentialRedaction.argumentForLog(last);
        }
        sink = last;
    }
}
