package org.mockserver.netty;

import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

public class LocalBoundIpAddressesTest {

    private static final List<String> REJECTIONS = Collections.singletonList("utun8: point-to-point, such as a VPN tunnel");

    @Test
    public void shouldFailOnBuildkiteNamingEachRejectedInterface() {
        AssertionError failure = assertThrows(AssertionError.class, () -> LocalBoundIpAddresses.noAddressQualifies(REJECTIONS, Map.of("BUILDKITE", "true")));
        assertThat(failure.getMessage(), containsString("utun8: point-to-point, such as a VPN tunnel"));
    }

    @Test
    public void shouldFailOnAnotherCi() {
        assertThrows(AssertionError.class, () -> LocalBoundIpAddresses.noAddressQualifies(REJECTIONS, Map.of("CI", "true")));
    }

    @Test
    public void shouldSkipOffCi() {
        assertSkipped(Collections.emptyMap());
        assertSkipped(Map.of("CI", "false"));
        assertSkipped(Map.of("BUILDKITE", ""));
    }

    private static void assertSkipped(Map<String, String> environment) {
        AssumptionViolatedException skipped = assertThrows(AssumptionViolatedException.class, () -> LocalBoundIpAddresses.noAddressQualifies(REJECTIONS, environment));
        assertThat(skipped.getMessage(), containsString("utun8: point-to-point, such as a VPN tunnel"));
    }
}
