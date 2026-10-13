package org.mockserver.configuration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Start-up warnings for the defaults the next major release changes to secure values: {@code forwardProxyBlockPrivateNetworks},
 * {@code localBoundIP}, {@code attemptToProxyIfNoMatchingExpectation} and the HTTP parsing limits. A warning is given only
 * while its property is left at the default, so setting the property (to any value) removes it.
 */
public final class UpcomingDefaultChanges {

    static final int NEXT_MAX_INITIAL_LINE_LENGTH = 8192;
    static final int NEXT_MAX_HEADER_SIZE = 16384;
    static final int NEXT_MAX_CHUNK_SIZE = 16384;

    static final AtomicBoolean STARTUP_WARNING_CLAIMED = new AtomicBoolean(false);

    private UpcomingDefaultChanges() {
    }

    /**
     * Claims the start-up warning, which is given once per JVM however many servers it starts.
     *
     * @return true for the first caller only
     */
    public static boolean claimStartupWarning() {
        return STARTUP_WARNING_CLAIMED.compareAndSet(false, true);
    }

    /**
     * The warnings that apply to a server started with this configuration; empty when every affected property is set.
     */
    public static List<String> warnings(Configuration configuration) {
        return warnings(configuration, property -> isSetOnInstance(configuration, property) || ConfigurationProperties.isExplicitlyConfigured(property));
    }

    static List<String> warnings(Configuration configuration, Predicate<String> explicitlyConfigured) {
        List<String> warnings = new ArrayList<>();
        if (!explicitlyConfigured.test("forwardProxyBlockPrivateNetworks") && !Boolean.TRUE.equals(configuration.forwardProxyBlockPrivateNetworks())) {
            warnings.add("forwardProxyBlockPrivateNetworks is not set, so forward and proxy actions can reach loopback, private-network and cloud-metadata addresses;"
                + " the next major release will change its default to true, which blocks them."
                + " Set mockserver.forwardProxyBlockPrivateNetworks (or MOCKSERVER_FORWARD_PROXY_BLOCK_PRIVATE_NETWORKS) to true to adopt the new default now,"
                + " or to false to keep reaching those addresses; either value removes this warning");
        }
        if (!explicitlyConfigured.test("localBoundIP") && isBlank(configuration.localBoundIP())) {
            warnings.add("localBoundIP is not set, so MockServer listens on every network interface and can be reached from other machines;"
                + " the next major release will change its default to 127.0.0.1, which only this machine can reach."
                + " Set mockserver.localBoundIP (or MOCKSERVER_LOCAL_BOUND_IP) to 127.0.0.1 to adopt the new default now,"
                + " or to 0.0.0.0 to keep listening on every interface, as a container or a shared test environment needs; either value removes this warning");
        }
        if (!explicitlyConfigured.test("attemptToProxyIfNoMatchingExpectation") && Boolean.TRUE.equals(configuration.attemptToProxyIfNoMatchingExpectation())) {
            warnings.add("attemptToProxyIfNoMatchingExpectation is not set, so a request that matches no expectation is forwarded to the host it names,"
                + " which makes MockServer an open proxy for anyone who can reach it;"
                + " the next major release will change its default to false, which returns 404 instead."
                + " Set mockserver.attemptToProxyIfNoMatchingExpectation (or MOCKSERVER_ATTEMPT_TO_PROXY_IF_NO_MATCHING_EXPECTATION) to false to adopt the new default now,"
                + " or to true to keep proxying unmatched requests; either value removes this warning");
        }
        List<String> limits = new ArrayList<>();
        addLimit(limits, explicitlyConfigured, "maxInitialLineLength", configuration.maxInitialLineLength(), NEXT_MAX_INITIAL_LINE_LENGTH);
        addLimit(limits, explicitlyConfigured, "maxHeaderSize", configuration.maxHeaderSize(), NEXT_MAX_HEADER_SIZE);
        addLimit(limits, explicitlyConfigured, "maxChunkSize", configuration.maxChunkSize(), NEXT_MAX_CHUNK_SIZE);
        if (!limits.isEmpty()) {
            warnings.add("the HTTP parsing limits " + String.join(", ", limits) + " are not set, and the next major release will lower their defaults to the values shown;"
                + " a request whose first line or headers exceed the new limits will then be refused with 414 or 431."
                + " Set each property to its new value to adopt it now, or to its current value to keep it; a property that is set is left out of this warning");
        }
        return warnings;
    }

    private static void addLimit(List<String> limits, Predicate<String> explicitlyConfigured, String property, Integer value, int nextDefault) {
        if (!explicitlyConfigured.test(property) && value != null && value > nextDefault) {
            limits.add("mockserver." + property + " (" + value + " bytes now, " + nextDefault + " next)");
        }
    }

    /**
     * Whether the property's field is set on this instance, which then wins over the static configuration.
     */
    static boolean isSetOnInstance(Configuration configuration, String property) {
        try {
            Field field = Configuration.class.getDeclaredField(property);
            field.setAccessible(true);
            return field.get(configuration) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }
}
