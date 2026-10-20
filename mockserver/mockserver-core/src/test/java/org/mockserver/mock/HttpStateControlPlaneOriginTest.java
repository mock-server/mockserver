package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class HttpStateControlPlaneOriginTest {

    private static final String FOREIGN = "https://attacker.example";
    private HttpState httpState;
    private ScheduledExecutorService schedulerExecutor;

    private HttpState httpState(Configuration configuration) {
        Scheduler scheduler = mock(Scheduler.class);
        schedulerExecutor = Executors.newScheduledThreadPool(1);
        when(scheduler.getExecutorService()).thenReturn(schedulerExecutor);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        return httpState;
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

    private static HttpRequest browser(String method, String path, String origin) {
        return request().withMethod(method).withPath(path).withHeader("host", "localhost:1080").withHeader("origin", origin);
    }

    @Test
    public void shouldRefuseAForeignOrNullOriginOnAnyControlPlaneRouteAndMethod() {
        HttpState state = httpState(configuration());

        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("GET", "/mockserver/retrieve", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("POST", "/mockserver/chaosExperiment/apply/x", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/reset", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/expectation", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("POST", "/chaosExperiment/apply/x", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("GET", "/_mockserver_ui_websocket", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", "null")), is(true));
    }

    @Test
    public void shouldNotRefuseAllowedOriginsMissingOriginsPreflightsOrMockedRoutes() {
        HttpState state = httpState(configuration());

        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", "http://localhost:3000")), is(false));
        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", "http://localhost:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(request().withMethod("PUT").withPath("/mockserver/reset")), is(false));
        assertThat("a preflight is answered without allow headers, not refused",
            state.isControlPlaneOriginRefused(browser("OPTIONS", "/mockserver/reset", FOREIGN).withHeader("access-control-request-method", "PUT")), is(false));
        assertThat("POST /graphql is not a control-plane route", state.isControlPlaneOriginRefused(browser("POST", "/graphql", FOREIGN)), is(false));
        assertThat("GET /status is not a control-plane route", state.isControlPlaneOriginRefused(browser("GET", "/status", FOREIGN)), is(false));
        assertThat(state.isControlPlaneOriginRefused(browser("POST", "/api/orders", FOREIGN)), is(false));
    }

    @Test
    public void shouldApplyTheAllowListAndTheLivenessPath() {
        HttpState state = httpState(configuration().controlPlaneCorsAllowOrigin("https://app.example.com").livenessHttpGetPath("/health"));

        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", "https://app.example.com")), is(false));
        assertThat(state.isControlPlaneOriginRefused(browser("PUT", "/mockserver/reset", "http://localhost:3000")), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("GET", "/health", FOREIGN)), is(true));
        assertThat(state.isControlPlaneOriginRefused(browser("POST", "/health", FOREIGN)), is(false));
    }

    @Test
    public void shouldClassifyAPreflightByTheMethodItPrecedes() {
        HttpState state = httpState(configuration());

        assertThat(state.isControlPlaneRequest(request().withMethod("OPTIONS").withPath("/reset")
            .withHeader("origin", FOREIGN).withHeader("access-control-request-method", "PUT")), is(true));
        assertThat(state.isControlPlaneRequest(request().withMethod("OPTIONS").withPath("/reset")
            .withHeader("origin", FOREIGN).withHeader("access-control-request-method", "POST")), is(false));
    }

    private static HttpRequest sameHost(String host, String origin) {
        return request().withMethod("PUT").withPath("/mockserver/reset").withHeader("host", host).withHeader("origin", origin);
    }

    @Test
    public void shouldRefuseARebindingPageWhoseOriginMatchesAPublicHost() {
        HttpState state = httpState(configuration());

        assertThat(state.isControlPlaneOriginRefused(sameHost("evil.example.com:1080", "http://evil.example.com:1080")), is(true));
        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver:1080", "http://mockserver:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver.default.svc.cluster.local:1080", "http://mockserver.default.svc.cluster.local:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(sameHost("host.docker.internal:1080", "http://host.docker.internal:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(sameHost("internal.evil.com:1080", "http://internal.evil.com:1080")), is(true));
        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver.corp.example:1080", "http://mockserver.corp.example:1080")), is(true));
    }

    @Test
    public void shouldAllowASameHostPageOnAConfiguredSuffixOrListedOrigin() {
        HttpState state = httpState(configuration()
            .controlPlaneTrustedHostSuffixes("corp.example")
            .controlPlaneCorsAllowOrigin("http://mockserver.example.com:1080"));

        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver.corp.example:1080", "http://mockserver.corp.example:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver.example.com:1080", "http://mockserver.example.com:1080")), is(false));
        assertThat(state.isControlPlaneOriginRefused(sameHost("evil.example.com:1080", "http://evil.example.com:1080")), is(true));
    }

    @Test
    public void shouldReportUnusableTrustedHostSuffixesAtStartup() {
        Configuration configuration = configuration().logLevel("WARN").controlPlaneTrustedHostSuffixes("co.uk, *, corp.example");
        MockServerLogger logger = spy(new MockServerLogger(configuration, MockServerLogger.class));
        List<String> reported = new ArrayList<>();
        doAnswer(invocation -> {
            LogEntry entry = invocation.getArgument(0);
            Object[] arguments = entry.getArguments();
            if (entry.getType() == LogEntry.LogMessageType.SERVER_CONFIGURATION && arguments != null && arguments.length == 1
                && String.valueOf(arguments[0]).startsWith("controlPlaneTrustedHostSuffixes entry")) {
                reported.add(entry.getLogLevel() + " " + arguments[0]);
            }
            return invocation.callRealMethod();
        }).when(logger).logEvent(any(LogEntry.class));
        Scheduler scheduler = mock(Scheduler.class);
        schedulerExecutor = Executors.newScheduledThreadPool(1);
        when(scheduler.getExecutorService()).thenReturn(schedulerExecutor);

        httpState = new HttpState(configuration, logger, scheduler);

        assertThat(reported.toString(), reported.size(), is(2));
        assertThat(reported.get(0), startsWith("ERROR controlPlaneTrustedHostSuffixes entry \"co.uk\" is REJECTED"));
        assertThat(reported.get(1), startsWith("WARN controlPlaneTrustedHostSuffixes entry \"*\" is not a host-name suffix"));
        assertThat(httpState.isControlPlaneOriginRefused(sameHost("evil.co.uk:1080", "http://evil.co.uk:1080")), is(true));
        assertThat(httpState.isControlPlaneOriginRefused(sameHost("mockserver.corp.example:1080", "http://mockserver.corp.example:1080")), is(false));
    }

    @Test
    public void shouldNotTreatAPageAsSameHostWhenItsRequestCarriesConflictingHosts() {
        HttpState state = httpState(configuration());

        assertThat(state.isControlPlaneOriginRefused(sameHost("mockserver:1080", "http://mockserver:1080").withHeader("host", "evil.example.com:1080")), is(true));
        assertThat(state.isControlPlaneOriginRefused(sameHost("evil.example.com:1080", "http://mockserver:1080").withHeader("host", "mockserver:1080")), is(true));
    }
}
