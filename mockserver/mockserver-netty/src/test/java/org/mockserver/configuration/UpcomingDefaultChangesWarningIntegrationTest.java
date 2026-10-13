package org.mockserver.configuration;

import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A server warns at start-up about each insecure default left in place that the next major release changes, once per
 * JVM, and not about a property set on its configuration.
 */
public class UpcomingDefaultChangesWarningIntegrationTest {

    private static List<String> upcomingDefaultWarnings(List<LogEntry> logged) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN)
            .map(LogEntry::getMessageFormat)
            .filter(message -> message.contains("the next major release"))
            .collect(Collectors.toList());
    }

    @Test
    public void warnsOnceAtStartUpAboutEachDefaultLeftInPlace() {
        List<LogEntry> logged = new CopyOnWriteArrayList<>();
        boolean previouslyClaimed = UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.getAndSet(false);
        MockServer first = null;
        MockServer second = null;
        try {
            MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
            Configuration configuration = configuration()
                .logLevel("WARN")
                .forwardProxyBlockPrivateNetworks(false)
                .attemptToProxyIfNoMatchingExpectation(true);

            first = new MockServer(configuration, 0);

            List<String> warnings = upcomingDefaultWarnings(logged);
            assertThat(warnings, is(UpcomingDefaultChanges.warnings(configuration)));
            assertThat(warnings, hasItem(startsWith("localBoundIP is not set")));
            assertThat(warnings, hasItem(startsWith("the HTTP parsing limits mockserver.maxInitialLineLength")));
            assertThat("set on the configuration, so not warned about", warnings, not(hasItem(containsString("forwardProxyBlockPrivateNetworks"))));
            assertThat("set on the configuration, so not warned about", warnings, not(hasItem(containsString("attemptToProxyIfNoMatchingExpectation"))));
            assertThat(UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.get(), is(true));

            logged.clear();
            second = new MockServer(configuration().logLevel("WARN"), 0);
            assertThat("given once per JVM", upcomingDefaultWarnings(logged), empty());
        } finally {
            MockServerLogger.setGlobalLogEventListener(null);
            stopQuietly(first);
            stopQuietly(second);
            UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.set(previouslyClaimed);
        }
    }

    @Test
    public void aServerThatDoesNotLogWarningsLeavesTheWarningForTheNextServer() {
        boolean previouslyClaimed = UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.getAndSet(false);
        MockServer quiet = null;
        try {
            quiet = new MockServer(configuration().logLevel("ERROR"), 0);
            assertThat(UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.get(), is(false));
        } finally {
            stopQuietly(quiet);
            UpcomingDefaultChanges.STARTUP_WARNING_CLAIMED.set(previouslyClaimed);
        }
    }
}
