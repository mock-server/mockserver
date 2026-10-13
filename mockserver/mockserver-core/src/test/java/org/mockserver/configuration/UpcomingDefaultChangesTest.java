package org.mockserver.configuration;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * The start-up warnings for the insecure defaults the next major release changes. Every value is set on the
 * {@link Configuration} instance and explicitness is passed in, so no global state is read or written.
 */
public class UpcomingDefaultChangesTest {

    private static final Predicate<String> NOTHING_SET = property -> false;

    private static Configuration currentDefaults() {
        return new Configuration()
            .forwardProxyBlockPrivateNetworks(false)
            .localBoundIP("")
            .attemptToProxyIfNoMatchingExpectation(true)
            .maxInitialLineLength(64 * 1024)
            .maxHeaderSize(256 * 1024)
            .maxChunkSize(Integer.MAX_VALUE);
    }

    private static Predicate<String> set(String... properties) {
        Set<String> set = Set.of(properties);
        return set::contains;
    }

    private static List<String> warningsAbout(List<String> warnings, String property) {
        return warnings.stream().filter(warning -> warning.contains(property)).collect(java.util.stream.Collectors.toList());
    }

    @Test
    public void warnsOnceForEachDefaultLeftInPlace() {
        List<String> warnings = UpcomingDefaultChanges.warnings(currentDefaults(), NOTHING_SET);

        assertThat(warnings, hasSize(4));
        assertThat(warningsAbout(warnings, "forwardProxyBlockPrivateNetworks"), hasSize(1));
        assertThat(warningsAbout(warnings, "localBoundIP"), hasSize(1));
        assertThat(warningsAbout(warnings, "attemptToProxyIfNoMatchingExpectation"), hasSize(1));
        assertThat(warningsAbout(warnings, "HTTP parsing limits"), hasSize(1));
    }

    @Test
    public void eachWarningNamesTheNewDefaultAndHowToKeepTheCurrentBehaviour() {
        List<String> warnings = UpcomingDefaultChanges.warnings(currentDefaults(), NOTHING_SET);

        String blockPrivateNetworks = warningsAbout(warnings, "forwardProxyBlockPrivateNetworks").get(0);
        assertThat(blockPrivateNetworks, containsString("default to true"));
        assertThat(blockPrivateNetworks, containsString("MOCKSERVER_FORWARD_PROXY_BLOCK_PRIVATE_NETWORKS"));
        assertThat(blockPrivateNetworks, containsString("or to false to keep"));

        String localBoundIP = warningsAbout(warnings, "localBoundIP").get(0);
        assertThat(localBoundIP, containsString("default to 127.0.0.1"));
        assertThat(localBoundIP, containsString("MOCKSERVER_LOCAL_BOUND_IP"));
        assertThat(localBoundIP, containsString("or to 0.0.0.0 to keep"));

        String proxyUnmatched = warningsAbout(warnings, "attemptToProxyIfNoMatchingExpectation").get(0);
        assertThat(proxyUnmatched, containsString("default to false"));
        assertThat(proxyUnmatched, containsString("returns 404"));
        assertThat(proxyUnmatched, containsString("or to true to keep"));

        String limits = warningsAbout(warnings, "HTTP parsing limits").get(0);
        assertThat(limits, containsString("mockserver.maxInitialLineLength (65536 bytes now, 8192 next)"));
        assertThat(limits, containsString("mockserver.maxHeaderSize (262144 bytes now, 16384 next)"));
        assertThat(limits, containsString("mockserver.maxChunkSize (2147483647 bytes now, 16384 next)"));
    }

    @Test
    public void settingAPropertyToEitherValueRemovesItsWarning() {
        assertThat(warningsAbout(UpcomingDefaultChanges.warnings(currentDefaults(), set("forwardProxyBlockPrivateNetworks")), "forwardProxyBlockPrivateNetworks"), empty());
        assertThat(warningsAbout(UpcomingDefaultChanges.warnings(currentDefaults(), set("localBoundIP")), "localBoundIP"), empty());
        assertThat(warningsAbout(UpcomingDefaultChanges.warnings(currentDefaults(), set("attemptToProxyIfNoMatchingExpectation")), "attemptToProxyIfNoMatchingExpectation"), empty());
        assertThat(UpcomingDefaultChanges.warnings(currentDefaults(), set("forwardProxyBlockPrivateNetworks")), hasSize(3));
    }

    @Test
    public void aParsingLimitThatIsSetIsLeftOutOfTheLimitsWarning() {
        List<String> warnings = UpcomingDefaultChanges.warnings(currentDefaults(), set("maxHeaderSize"));
        String limits = warningsAbout(warnings, "HTTP parsing limits").get(0);
        assertThat(limits, containsString("maxInitialLineLength"));
        assertThat(limits, containsString("maxChunkSize"));
        assertThat(limits, not(containsString("maxHeaderSize")));

        List<String> allLimitsSet = UpcomingDefaultChanges.warnings(currentDefaults(), set("maxInitialLineLength", "maxHeaderSize", "maxChunkSize"));
        assertThat(warningsAbout(allLimitsSet, "HTTP parsing limits"), empty());
        assertThat(allLimitsSet, hasSize(3));
    }

    @Test
    public void noWarningWhenEveryPropertyIsSet() {
        assertThat(UpcomingDefaultChanges.warnings(currentDefaults(), property -> true), empty());
    }

    @Test
    public void noWarningForAValueThatIsAlreadyTheNewDefault() {
        Configuration secure = new Configuration()
            .forwardProxyBlockPrivateNetworks(true)
            .localBoundIP("127.0.0.1")
            .attemptToProxyIfNoMatchingExpectation(false)
            .maxInitialLineLength(8192)
            .maxHeaderSize(16384)
            .maxChunkSize(16384);

        assertThat(UpcomingDefaultChanges.warnings(secure, NOTHING_SET), empty());
    }

    @Test
    public void aPropertySetOnTheInstanceCountsAsSet() {
        Configuration configuration = currentDefaults();
        for (String property : List.of("forwardProxyBlockPrivateNetworks", "localBoundIP", "attemptToProxyIfNoMatchingExpectation", "maxInitialLineLength", "maxHeaderSize", "maxChunkSize")) {
            assertThat(property, UpcomingDefaultChanges.isSetOnInstance(configuration, property), is(true));
            assertThat(property, UpcomingDefaultChanges.isSetOnInstance(new Configuration(), property), is(false));
        }
        // every property is set on the instance, so the live check warns about none of them
        assertThat(UpcomingDefaultChanges.warnings(configuration), empty());
    }

    @Test
    public void theStartUpWarningIsClaimedOnce() {
        boolean previous = UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.getAndSet(false);
        try {
            assertThat(UpcomingDefaultChanges.claimStartupWarning(), is(true));
            assertThat(UpcomingDefaultChanges.claimStartupWarning(), is(false));
        } finally {
            UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.set(previous);
        }
    }
}
