package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.llm.OpenAiResponsesStore;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.audit.AuditStore;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Behavioural tests for {@link HttpState#applyConfigurationUpdate()} — the reconciliation run
 * immediately after {@code PUT /mockserver/configuration} mutates the live {@link Configuration}.
 *
 * <p>Two distinct contracts are covered:
 * <ul>
 *     <li><b>Resizable capacity properties actually resize the running subsystem.</b> Previously
 *     {@code maxLogEntries}, {@code maxExpectations} and {@code controlPlaneAuditMaxEntries} were
 *     consumed once at construction, so a PUT was accepted, echoed back and then ignored.</li>
 *     <li><b>Genuinely init-only properties are reported, not silently dropped.</b>
 *     {@code ringBufferSize} and {@code maxWebSocketExpectations} cannot be resized on a running
 *     server, so a differing value produces a WARN and the configuration field is reset to the
 *     value in force (making the echoed response and a later GET truthful). An unchanged value —
 *     the whole-blob PUT case — must warn about nothing.</li>
 * </ul>
 *
 * <p>State-mutating: asserts on the process-wide {@link AuditStore} singleton, so it must run in
 * the sequential Surefire phase. The singleton's capacity is restored in {@link #restoreAuditStore()}.
 */
public class HttpStateConfigurationUpdateTest {

    /**
     * Captures every event logged through this logger so the init-only WARN can be asserted without
     * touching the process-wide global log listener (which would not be parallel-safe).
     */
    private static class CapturingMockServerLogger extends MockServerLogger {
        /**
         * Snapshots (level, message) taken at log time. The {@link LogEntry} instances themselves
         * MUST NOT be retained: they are recycled and cleared once handed to the event log's
         * disruptor, so reading them later would see blanked fields.
         */
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        CapturingMockServerLogger(Configuration configuration) {
            super(configuration, HttpStateConfigurationUpdateTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            if (logEntry.getLogLevel() == Level.WARN) {
                warnings.add(String.valueOf(logEntry.getMessage()));
            }
            super.logEvent(logEntry);
        }

        List<String> warnings() {
            return warnings;
        }

        /**
         * Discards everything captured so far, so a warning assertion sees only what the action
         * under test logged (and not, say, anything emitted while the fixture was built).
         */
        void forgetCapturedEvents() {
            warnings.clear();
        }
    }

    private Configuration configuration;
    private CapturingMockServerLogger logger;
    private HttpState httpState;
    private int originalAuditMaxSize;

    @Before
    public void prepareTestFixture() {
        originalAuditMaxSize = AuditStore.getInstance().getMaxSize();
        configuration = configuration();
        // The suite runs at logLevel=ERROR (mockserver.testLogLevel), which would suppress the
        // init-only WARN under test. Raise it on this INSTANCE configuration only — no global state.
        configuration.logLevel(Level.WARN);
        logger = new CapturingMockServerLogger(configuration);
        httpState = new HttpState(configuration, logger, mock(Scheduler.class));
    }

    @After
    public void restoreAuditStore() {
        AuditStore.getInstance().setMaxSize(originalAuditMaxSize);
        OpenAiResponsesStore.getInstance().reset();
        OpenAiResponsesStore.getInstance().setMaxSizeInBytes(configuration().maxOpenAiResponsesSizeInBytes());
        if (httpState != null) {
            httpState.stop();
        }
    }

    /**
     * Blocks until the asynchronous event log has drained the disruptor, so log-size assertions are
     * deterministic.
     */
    private void drainEventLog() {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveMessageLogEntries((RequestDefinition) null, future::complete);
        try {
            future.get(60, SECONDS);
        } catch (Exception e) {
            fail("timed out draining the event log: " + e.getMessage());
        }
    }

    private void addExpectation(String path) {
        httpState.getRequestMatchers().add(new Expectation(request(path)).thenRespond(response(path)), API);
    }

    @Test
    public void shouldResizeEventLogWhenMaxLogEntriesReduced() {
        // given - five entries in the event log
        for (int i = 0; i < 5; i++) {
            logger.logEvent(new LogEntry().setType(RECEIVED_REQUEST).setHttpRequest(request("/" + i)));
        }
        drainEventLog();
        assertThat(httpState.getMockServerLog().size(), is(5));

        // when - maxLogEntries is reduced, as a PUT /mockserver/configuration would
        configuration.maxLogEntries(2);
        httpState.applyConfigurationUpdate();

        // then - the running event log is resized immediately, not merely recorded in the config
        assertThat(httpState.getMockServerLog().size(), is(2));
    }

    @Test
    public void shouldBoundEventLogBySubsequentAddsAfterMaxLogEntriesReduced() {
        // given - maxLogEntries reduced on an empty log
        configuration.maxLogEntries(3);
        httpState.applyConfigurationUpdate();

        // when - more entries than the new bound arrive
        for (int i = 0; i < 8; i++) {
            logger.logEvent(new LogEntry().setType(RECEIVED_REQUEST).setHttpRequest(request("/" + i)));
        }
        drainEventLog();

        // then - the new bound is enforced on the running log
        assertThat(httpState.getMockServerLog().size(), is(3));
    }

    @Test
    public void shouldApplyAndResizeOpenAiResponsesByteBudget() {
        // given - the server's own budget was applied when it started
        assertThat(OpenAiResponsesStore.getInstance().getMaxSizeInBytes(), is(configuration.maxOpenAiResponsesSizeInBytes()));
        String body = "{\"tools\":\"" + new String(new char[10_000]).replace('\0', 't') + "\"}";
        for (int i = 0; i < 4; i++) {
            OpenAiResponsesStore.getInstance().put(new OpenAiResponsesStore.StoredResponse("resp_" + i, ParsedConversation.empty(), body));
        }
        assertThat(OpenAiResponsesStore.getInstance().size(), is(4));

        // when - the budget is lowered, as a PUT /mockserver/configuration would
        configuration.maxOpenAiResponsesSizeInBytes(25_000L);
        httpState.applyConfigurationUpdate();

        // then - the least recently used responses are evicted from the running store immediately
        assertThat(OpenAiResponsesStore.getInstance().getMaxSizeInBytes(), is(25_000L));
        assertThat(OpenAiResponsesStore.getInstance().size(), is(2));
        assertThat(OpenAiResponsesStore.getInstance().get("resp_3").isPresent(), is(true));
        assertThat(OpenAiResponsesStore.getInstance().get("resp_0").isPresent(), is(false));
    }

    @Test
    public void shouldResizeExpectationStoreWhenMaxExpectationsReduced() {
        // given - four expectations
        addExpectation("/one");
        addExpectation("/two");
        addExpectation("/three");
        addExpectation("/four");
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null), hasSize(4));

        // when - maxExpectations is reduced, as a PUT /mockserver/configuration would
        configuration.maxExpectations(2);
        httpState.applyConfigurationUpdate();

        // then - the eldest expectations are evicted from the running store immediately
        List<Expectation> remaining = httpState.getRequestMatchers().retrieveActiveExpectations(null);
        assertThat(remaining, hasSize(2));
        assertThat(
            remaining.stream().map(e -> ((HttpRequest) e.getHttpRequest()).getPath().getValue()).collect(Collectors.toList()),
            contains("/three", "/four")
        );
    }

    @Test
    public void shouldBoundExpectationStoreBySubsequentAddsAfterMaxExpectationsReduced() {
        // given - maxExpectations reduced on an empty store
        configuration.maxExpectations(2);
        httpState.applyConfigurationUpdate();

        // when - more expectations than the new bound are added
        addExpectation("/one");
        addExpectation("/two");
        addExpectation("/three");

        // then - the new bound is enforced on the running store
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null), hasSize(2));
    }

    @Test
    public void shouldResizeAuditStoreWhenControlPlaneAuditMaxEntriesChanged() {
        // given - a capacity distinct from whatever the singleton was constructed with. Deliberately
        // LARGER than the default so this test can never evict another test's audit entries.
        int newMaxSize = originalAuditMaxSize + 4321;

        // when
        configuration.controlPlaneAuditMaxEntries(newMaxSize);
        httpState.applyConfigurationUpdate();

        // then - the running audit store is resized, not just the Configuration field
        assertThat(AuditStore.getInstance().getMaxSize(), is(newMaxSize));
    }

    /**
     * Applies {@code supplied} exactly as {@code PUT /mockserver/configuration} does: mutate the
     * live Configuration, then reconcile.
     */
    private void put(ConfigurationDTO supplied) {
        logger.forgetCapturedEvents();
        supplied.applyTo(configuration);
        httpState.applyConfigurationUpdate(supplied);
    }

    @Test
    public void shouldWarnAndResetRingBufferSizeBecauseItIsFixedAtStartup() {
        // given - the value the disruptor was actually built with
        int inForce = httpState.getMockServerLog().getRingBufferSizeInForce();
        int supplied = inForce * 2;

        // when - a PUT explicitly supplies a different value
        put(new ConfigurationDTO().setRingBufferSize(supplied));

        // then - it is NOT silently accepted: a WARN names the property, the ignored value and the
        // value actually in force
        List<String> warnings = logger.warnings();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0), containsString("ringBufferSize"));
        assertThat(warnings.get(0), containsString("fixed at startup"));
        assertThat(warnings.get(0), containsString(String.valueOf(supplied)));
        assertThat(warnings.get(0), containsString(String.valueOf(inForce)));

        // and - the configuration is reset to the truth, so the echoed response and a later
        // GET /mockserver/configuration do not report a value the server is not using
        assertThat(configuration.ringBufferSize(), is(inForce));
    }

    @Test
    public void shouldWarnAndResetMaxWebSocketExpectationsBecauseItIsFixedAtStartup() {
        // given
        int inForce = configuration.maxWebSocketExpectations();
        int supplied = inForce + 500;

        // when - a PUT explicitly supplies a different value
        put(new ConfigurationDTO().setMaxWebSocketExpectations(supplied));

        // then
        List<String> warnings = logger.warnings();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0), containsString("maxWebSocketExpectations"));
        assertThat(warnings.get(0), containsString("fixed at startup"));
        assertThat(warnings.get(0), containsString(String.valueOf(supplied)));
        assertThat(warnings.get(0), containsString(String.valueOf(inForce)));
        assertThat(configuration.maxWebSocketExpectations(), is(inForce));
    }

    @Test
    public void shouldNotLetAPutWidenTheForwardProxyHostLists() {
        // given
        configuration.forwardProxyAllowedHosts("api.example.com").forwardProxyDeniedHosts("10.0.0.0/8");

        // when - a PUT tries to clear both lists
        put(new ConfigurationDTO().setForwardProxyAllowedHosts("").setForwardProxyDeniedHosts(""));

        // then - both stay in force, and each ignored value is reported
        assertThat(configuration.forwardProxyAllowedHosts(), is("api.example.com"));
        assertThat(configuration.forwardProxyDeniedHosts(), is("10.0.0.0/8"));
        List<String> warnings = logger.warnings();
        assertThat(warnings, hasSize(2));
        assertThat(warnings.get(0), containsString("forwardProxyAllowedHosts is fixed at startup"));
        assertThat(warnings.get(1), containsString("forwardProxyDeniedHosts is fixed at startup"));
    }

    @Test
    public void shouldNotLetAPutSwitchOffForwardProxyBlockPrivateNetworks() {
        // given - the SSRF guard is on
        configuration.forwardProxyBlockPrivateNetworks(true);

        // when - a PUT tries to switch it off
        put(new ConfigurationDTO().setForwardProxyBlockPrivateNetworks(false));

        // then - it stays on, and the ignored value is reported
        assertThat(configuration.forwardProxyBlockPrivateNetworks(), is(true));
        List<String> warnings = logger.warnings();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0), containsString("forwardProxyBlockPrivateNetworks is fixed at startup"));
        assertThat(warnings.get(0), containsString("the supplied value false has been IGNORED"));
    }

    @Test
    public void shouldNotLetAPutRepointTheStaticFilesDirectory() {
        // given
        configuration.staticFilesDirectory("__files");

        // when
        put(new ConfigurationDTO().setStaticFilesDirectory("/"));

        // then
        assertThat(configuration.staticFilesDirectory(), is("__files"));
        List<String> warnings = logger.warnings();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0), containsString("staticFilesDirectory is fixed at startup"));
    }

    @Test
    public void shouldNotWarnWhenInitOnlyPropertiesAreEchoedBackUnchanged() {
        // given - the whole-blob PUT case: a client GETs the configuration and PUTs it back
        // verbatim, including the init-only properties. That must not produce noise.
        ConfigurationDTO wholeBlob = new ConfigurationDTO(configuration);

        // when
        put(wholeBlob);

        // then
        assertThat(logger.warnings(), is(empty()));
        assertThat(configuration.ringBufferSize(), is(httpState.getMockServerLog().getRingBufferSizeInForce()));
    }

    @Test
    public void shouldWarnWhenPutLowersTlsPosture() {
        // given - a secure starting posture
        configuration
            .tlsMutualAuthenticationRequired(true)
            .forwardProxyTLSX509CertificatesTrustManagerType(org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager.JVM)
            .forwardProxyTLSHostnameVerificationEnabled(true);
        logger.forgetCapturedEvents();

        // when - a PUT downgrades trust to ANY, turns mTLS off and turns host name verification off
        ConfigurationDTO supplied = new ConfigurationDTO()
            .setForwardProxyTLSX509CertificatesTrustManagerType("ANY")
            .setTlsMutualAuthenticationRequired(false)
            .setForwardProxyTLSHostnameVerificationEnabled(false);
        // audit runs against the pre-change configuration, exactly as the PUT handler orders it
        httpState.warnIfLoweringTlsPosture(supplied);

        // then - a single audit WARN names each downgrade
        assertThat(logger.warnings(), hasSize(1));
        String warning = logger.warnings().get(0);
        assertThat(warning, containsString("lowers or alters security posture"));
        assertThat(warning, containsString("-> ANY"));
        assertThat(warning, containsString("tlsMutualAuthenticationRequired true -> false"));
        assertThat(warning, containsString("forwardProxyTLSHostnameVerificationEnabled true -> false"));
    }

    @Test
    public void shouldNotWarnWhenPutDoesNotLowerTlsPosture() {
        // given - a secure starting posture
        configuration
            .tlsMutualAuthenticationRequired(true)
            .forwardProxyTLSX509CertificatesTrustManagerType(org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager.JVM);
        logger.forgetCapturedEvents();

        // when - a PUT that keeps mTLS on and TIGHTENS nothing about TLS (raising trust is not a downgrade)
        ConfigurationDTO supplied = new ConfigurationDTO()
            .setTlsMutualAuthenticationRequired(true)
            .setMaxLogEntries(50);
        httpState.warnIfLoweringTlsPosture(supplied);

        // then - no posture WARN
        assertThat(logger.warnings(), is(empty()));
    }

    @Test
    public void shouldNotWarnAboutRingBufferSizeWhenOnlyMaxLogEntriesSupplied() {
        // given - ringBufferSize DERIVES from maxLogEntries when not set explicitly, so changing
        // maxLogEntries alone moves the resolved ringBufferSize. That must not be reported as an
        // ignored ringBufferSize change - the client never asked for one - and must not pin
        // ringBufferSize to a literal value (which would break the derivation permanently).
        // when
        put(new ConfigurationDTO().setMaxLogEntries(50));

        // then
        assertThat(logger.warnings(), is(empty()));
    }

    @Test
    public void shouldApplyPutOverloadLimitsToTheRunningScheduler() {
        // given - a real asynchronous scheduler behind the running state, with room for many delayed responses
        configuration.maxPendingDelayedResponses(100);
        Scheduler scheduler = new Scheduler(configuration, logger, false);
        httpState.stop();
        httpState = new HttpState(configuration, logger, scheduler);
        try {
            java.util.concurrent.atomic.AtomicInteger refused = new java.util.concurrent.atomic.AtomicInteger();
            Runnable delayedResponse = Scheduler.rejectable(() -> {
            }, refused::incrementAndGet);
            scheduler.schedule(delayedResponse, false, org.mockserver.model.Delay.seconds(30));
            scheduler.schedule(delayedResponse, false, org.mockserver.model.Delay.seconds(30));
            assertThat(refused.get(), is(0));

            // when - PUT /mockserver/configuration lowers the limit below the backlog
            put(new ConfigurationDTO().setMaxPendingDelayedResponses(2));
            scheduler.schedule(delayedResponse, false, org.mockserver.model.Delay.seconds(30));

            // then - the running scheduler enforces the new limit at once
            assertThat(refused.get(), is(1));
            assertThat(scheduler.getPendingDelayedResponseCount(), is(2));
        } finally {
            scheduler.shutdown();
        }
    }
}
