package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
}
