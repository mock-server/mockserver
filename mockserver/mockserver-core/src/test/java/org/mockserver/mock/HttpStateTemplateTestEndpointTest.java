package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The template dry run is served only at {@code PUT /mockserver/template/test}, behind control-plane
 * authentication, and it needs a role that may change expectations because it runs user-supplied code.
 */
public class HttpStateTemplateTestEndpointTest {

    private static final String VELOCITY_TEMPLATE = "{\"templateType\":\"VELOCITY\",\"template\":\"{ \\\"statusCode\\\": 200, \\\"body\\\": \\\"$request.path\\\" }\",\"request\":{\"path\":\"/sample\"}}";

    private HttpState httpState;
    private Scheduler scheduler;

    private static class CapturingResponseWriter extends ResponseWriter {
        final List<HttpResponse> responses = new CopyOnWriteArrayList<>();

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            responses.add(response);
        }

        HttpResponse awaitResponse() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (responses.isEmpty() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat("a response was written", responses.isEmpty(), is(false));
            return responses.get(0);
        }
    }

    private void createHttpState(Configuration configuration) {
        MockServerLogger mockServerLogger = new MockServerLogger(configuration, HttpStateTemplateTestEndpointTest.class);
        scheduler = new Scheduler(configuration, mockServerLogger, true);
        httpState = new HttpState(configuration, mockServerLogger, scheduler);
    }

    @After
    public void tearDown() {
        if (httpState != null) {
            httpState.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private static HttpRequest templateTest(String path) {
        return request().withMethod("PUT").withPath(path).withBody(VELOCITY_TEMPLATE);
    }

    @Test
    public void rendersTheTemplateAndWritesTheReport() throws Exception {
        createHttpState(configuration());
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();

        assertThat(httpState.handle(templateTest("/mockserver/template/test"), responseWriter, false), is(true));

        HttpResponse response = responseWriter.awaitResponse();
        assertThat(response.getStatusCode(), is(200));
        JsonNode report = ObjectMapperFactory.createObjectMapper().readTree(response.getBodyAsString());
        assertThat(report.path("valid").asBoolean(), is(true));
        assertThat(report.path("renderedResponse").path("body").asText(), is("/sample"));
    }

    @Test
    public void writesTheReportBeforeReturningInAServletContainer() {
        createHttpState(configuration());
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();

        assertThat(httpState.handle(templateTest("/mockserver/template/test"), responseWriter, true), is(true));

        assertThat(responseWriter.responses.size(), is(1));
        assertThat(responseWriter.responses.get(0).getStatusCode(), is(200));
    }

    @Test
    public void hasNoBarePathAliasSoItCannotShadowAMock() {
        createHttpState(configuration());

        assertThat(httpState.handle(templateTest("/template/test"), new CapturingResponseWriter(), false), is(false));
    }

    @Test
    public void refusesAnUnauthenticatedCaller() throws Exception {
        createHttpState(configuration());
        httpState.setControlPlaneAuthenticationHandler(request -> false);
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();

        httpState.handle(templateTest("/mockserver/template/test"), responseWriter, false);

        assertThat(responseWriter.awaitResponse().getStatusCode(), is(401));
        TimeUnit.MILLISECONDS.sleep(500);
        assertThat("no dry-run report follows the refusal", responseWriter.responses.size(), is(1));
    }

    @Test
    public void refusesAReadOnlyPrincipal() throws Exception {
        CapturingResponseWriter responseWriter = templateTestAs(Set.of("viewers"));

        assertThat(responseWriter.awaitResponse().getStatusCode(), is(403));
    }

    @Test
    public void allowsAPrincipalThatMayChangeExpectations() throws Exception {
        CapturingResponseWriter responseWriter = templateTestAs(Set.of("qa-team"));

        assertThat(responseWriter.awaitResponse().getStatusCode(), is(200));
    }

    private CapturingResponseWriter templateTestAs(Set<String> scopes) {
        Map<String, ControlPlaneRole> mapping = new LinkedHashMap<>();
        mapping.put("qa-team", ControlPlaneRole.MUTATE);
        mapping.put("viewers", ControlPlaneRole.READ);
        createHttpState(configuration().controlPlaneAuthorizationEnabled(true).controlPlaneScopeMapping(mapping));
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated("tester", "verified-oidc", Map.of("sub", "tester"), scopes);
            }
        });
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        httpState.handle(templateTest("/mockserver/template/test"), responseWriter, false);
        return responseWriter;
    }
}
