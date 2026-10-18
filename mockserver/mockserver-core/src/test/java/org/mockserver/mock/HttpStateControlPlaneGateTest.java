package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.HttpState.PATH_PREFIX;
import static org.mockserver.mock.HttpState.isControlPlanePathCandidate;
import static org.mockserver.model.HttpRequest.request;

/**
 * Differential corpus for the cheapest-first control-plane gate
 * ({@link HttpState#isControlPlanePathCandidate(String)}).
 * <p>
 * The gate is a necessary condition used only as a fast reject; the per-method dispatch chains in
 * {@link HttpState#handle} and {@code HttpRequestHandler.channelRead0} remain the real routers. So
 * the correctness bar is: the gate must return {@code true} for EVERY control-plane path (both its
 * {@code /mockserver}-prefixed form and its bare alias), it should return {@code false} for
 * data-plane paths so they take the fast reject, and — regardless of either — the routing DECISION
 * that {@code handle} reaches must be unchanged. The enumeration here is the completeness evidence.
 */
public class HttpStateControlPlaneGateTest {

    // Every bare control-plane alias accepted anywhere in either dispatch chain. Enumerated from the
    // route definitions; the gate carries the same set, so a transcription error in either is caught
    // by {@link #everyControlPlanePathIsACandidate}.
    private static final String[] BARE_ALIASES = {
            "/asyncapi",
            "/asyncapi/http",
            "/asyncapi/verify",
            "/audit",
            "/baseline/compare",
            "/bind",
            "/breakpoint/matcher",
            "/breakpoint/matcher/clear",
            "/breakpoint/matcher/remove",
            "/breakpoint/matchers",
            "/cassettes",
            "/chaosExperiment",
            "/chaosExperiment/history",
            "/chaosExperiment/profiles",
            "/clear",
            "/clock",
            "/cluster",
            "/config",
            "/configuration",
            "/contractTest",
            "/crud",
            "/debugMismatch",
            "/diff",
            "/drift",
            "/drift/clear",
            "/expectation",
            "/explainUnmatched",
            "/files/delete",
            "/files/list",
            "/files/retrieve",
            "/files/store",
            "/generateExpectation",
            "/graphql",
            "/grpc/clear",
            "/grpc/descriptors",
            "/grpc/health",
            "/grpc/services",
            "/grpcChaos",
            "/http3status",
            "/import",
            "/llm/diffRuns",
            "/llm/optimisationReport",
            "/loadScenario",
            "/loadScenario/generateFromOpenAPI",
            "/loadScenario/generateFromRecording",
            "/loadScenario/start",
            "/loadScenario/stop",
            "/logEntryBody",
            "/mode",
            "/oidc",
            "/openapi",
            "/pact",
            "/pact/import",
            "/pact/verify",
            "/preemption",
            "/proxyConfiguration",
            "/ready",
            "/recordings/promote",
            "/replay",
            "/reset",
            "/retrieve",
            "/saml",
            "/scenario",
            "/scim",
            "/serviceChaos",
            "/status",
            "/stop",
            "/tcpChaos",
            "/trafficValidate",
            "/verify",
            "/verifySequence",
            "/verifySLO",
            "/wasm/modules",
            "/wasm/test",
            "/wsdl",
    };

    // Bare {name}-style routes matched by prefix rather than by exact path.
    private static final String[] BARE_PREFIX_SAMPLES = {
        "/scenario/demo",
        "/loadScenario/demo",
        "/chaosExperiment/profiles/demo",
        "/chaosExperiment/apply/demo",
    };

    // Prefixed-only control-plane routes with no bare alias (dashboard, openapi spec, metrics).
    private static final String[] PREFIXED_ONLY = {
        PATH_PREFIX + "/dashboard",
        PATH_PREFIX + "/dashboard/main.js",
        PATH_PREFIX + "/openapi.yaml",
        PATH_PREFIX + "/metrics",
        PATH_PREFIX + "/scenario",
    };

    // Data-plane paths (including deliberate near-misses) that must NOT be gated as control-plane.
    private static final String[] DATA_PLANE = {
        "/",
        "",
        "/some/user/api",
        "/order/123",
        "/expectationx",
        "/expectation/extra",
        "/mockserverfoo",
        "/mockserver",
        "/scenariofoo",
        "/loadScenariofoo",
        "/health",
        "/metrics",
        "/dashboard",
    };

    private Configuration configuration;
    private HttpState httpState;
    private ScheduledExecutorService schedulerExecutor;

    @Before
    public void prepare() {
        configuration = configuration();
        Scheduler scheduler = mock(Scheduler.class);
        schedulerExecutor = java.util.concurrent.Executors.newScheduledThreadPool(2);
        when(scheduler.getExecutorService()).thenReturn(schedulerExecutor);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
    }

    @After
    public void cleanup() {
        if (httpState != null) {
            httpState.stop();
        }
        if (schedulerExecutor != null) {
            schedulerExecutor.shutdownNow();
        }
    }

    private static final class CapturingResponseWriter extends ResponseWriter {
        volatile HttpResponse response;

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    // ---- gate completeness: the enumeration is a superset of every control-plane path ----

    @Test
    public void everyControlPlanePathIsACandidate() {
        for (String bare : BARE_ALIASES) {
            assertThat("bare alias must be a candidate: " + bare, isControlPlanePathCandidate(bare), is(true));
            assertThat("prefixed form must be a candidate: " + PATH_PREFIX + bare,
                isControlPlanePathCandidate(PATH_PREFIX + bare), is(true));
        }
        for (String bare : BARE_PREFIX_SAMPLES) {
            assertThat("bare prefix route must be a candidate: " + bare, isControlPlanePathCandidate(bare), is(true));
            assertThat("prefixed form must be a candidate: " + PATH_PREFIX + bare,
                isControlPlanePathCandidate(PATH_PREFIX + bare), is(true));
        }
        for (String prefixed : PREFIXED_ONLY) {
            assertThat("prefixed-only route must be a candidate: " + prefixed,
                isControlPlanePathCandidate(prefixed), is(true));
        }
    }

    @Test
    public void dataPlanePathsAreNotCandidates() {
        for (String dataPlane : DATA_PLANE) {
            assertThat("data-plane path must not be gated as control-plane: '" + dataPlane + "'",
                isControlPlanePathCandidate(dataPlane), is(false));
        }
        assertThat("null path is never a candidate", isControlPlanePathCandidate(null), is(false));
    }

    // ---- routing corpus: handle()'s decision is unchanged (invariant to the gate as a fast path) ----

    private boolean route(String method, String path) {
        return httpState.handle(request().withMethod(method).withPath(path), new CapturingResponseWriter(), false);
    }

    @Test
    public void controlPlaneRoutesAreHandledInBothForms() {
        assertThat(route("PUT", PATH_PREFIX + "/reset"), is(true));
        assertThat(route("PUT", PATH_PREFIX + "/clear"), is(true));
        assertThat(route("GET", PATH_PREFIX + "/config"), is(true));
        assertThat(route("GET", PATH_PREFIX + "/proxyConfiguration"), is(true));
        assertThat(route("GET", PATH_PREFIX + "/cluster"), is(true));
    }

    @Test
    public void shouldRouteBareResetThroughControlPlane() {
        assertThat("bare /reset must still route to the control plane", route("PUT", "/reset"), is(true));
    }

    @Test
    public void bareControlPlaneRoutesAreHandled() {
        assertThat(route("PUT", "/clear"), is(true));
        assertThat(route("GET", "/config"), is(true));
        assertThat(route("GET", "/proxyConfiguration"), is(true));
        assertThat(route("GET", "/cluster"), is(true));
    }

    @Test
    public void dataPlaneRequestsAreNotHandledByControlPlane() {
        assertThat(route("GET", "/some/user/api"), is(false));
        assertThat(route("PUT", "/some/user/api"), is(false));
        assertThat(route("POST", "/order/123"), is(false));
        assertThat(route("GET", "/expectationx"), is(false));
        assertThat(route("PUT", "/mockserverfoo"), is(false));
        assertThat(route("GET", "/"), is(false));
        assertThat(route("GET", ""), is(false));
    }
}
