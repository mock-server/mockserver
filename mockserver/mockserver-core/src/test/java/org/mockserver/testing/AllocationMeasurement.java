package org.mockserver.testing;

import org.junit.Assume;
import org.mockserver.model.HttpRequest;

import java.lang.management.ManagementFactory;

import static org.mockserver.model.HttpRequest.request;

/**
 * Per-thread allocation measurement for allocation-bound tests that call {@link HttpRequest}.
 * <p>
 * A Mockito spy of an {@code HttpRequest} anywhere in the same JVM leaves the class instrumented, and from then on
 * every call of its methods allocates. A bound measured in such a JVM measures Mockito, so these tests skip there and
 * are also run in the {@code allocation-tests-without-c2} execution, a fresh JVM per class where they always run.
 * Probe after measuring, before asserting: a spy in a test running in parallel can instrument the class mid-measurement,
 * and the instrumentation is never removed, so a later probe sees any that happened during the measurement.
 */
public final class AllocationMeasurement {

    private static final int PROBE_CALLS = 100_000;
    private static volatile Object sink;

    private AllocationMeasurement() {
    }

    public static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /**
     * Skips the calling test when a plain {@code HttpRequest} getter allocates, which it never does uninstrumented.
     */
    public static void assumeHttpRequestIsNotInstrumented() {
        HttpRequest probe = request().withPath("/probe");
        for (int i = 0; i < PROBE_CALLS; i++) {
            sink = probe.getRemoteAddressBytes();
        }
        long before = allocatedBytes();
        for (int i = 0; i < PROBE_CALLS; i++) {
            sink = probe.getRemoteAddressBytes();
        }
        long perCall = (allocatedBytes() - before) / PROBE_CALLS;
        String reason = "HttpRequest is instrumented in this JVM (" + perCall + " bytes per getter call), so its allocations cannot be measured here";
        if (ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-XX:TieredStopAtLevel=1")) {
            // the dedicated clean-JVM execution: never skip there, or the bound would go unchecked everywhere
            if (perCall != 0) {
                throw new AssertionError(reason);
            }
            return;
        }
        Assume.assumeTrue(reason, perCall == 0);
    }
}
