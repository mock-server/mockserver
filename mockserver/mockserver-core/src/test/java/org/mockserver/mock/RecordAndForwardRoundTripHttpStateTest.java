package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Behavioural tests for the one-command record round-trip (Unit R): the
 * {@code ?forwardUnmatchedTo=<upstream>} extension to
 * {@code GET /mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=...}.
 * <p>
 * When an upstream is supplied the retrieve call arms record-and-forward of
 * unmatched requests to that upstream (so subsequent traffic is recorded), and
 * the same/next retrieve returns the recorded expectations in the requested
 * format. The tests prove the observable behaviour: the param arms recording,
 * the upstream is SSRF-validated before any state is mutated, and recorded
 * traffic round-trips through retrieve-as-code / retrieve-as-JSON and re-imports
 * to expectations that match the recorded requests.
 * <p>
 * Uses a per-instance {@link Configuration} so it never mutates global
 * {@code ConfigurationProperties} state and needs no two-phase Surefire
 * registration.
 *
 * @author jamesdbloom
 */
public class RecordAndForwardRoundTripHttpStateTest {

    private final List<HttpState> httpStates = new ArrayList<>();

    private final RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(new MockServerLogger());
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());

    private static class FakeResponseWriter extends ResponseWriter {
        public HttpResponse response;

        protected FakeResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    @After
    public void stopHttpStates() {
        httpStates.forEach(HttpState::stop);
    }

    private HttpState newHttpState(Configuration configuration) {
        Scheduler scheduler = mock(Scheduler.class);
        HttpState httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        httpStates.add(httpState);
        return httpState;
    }

    private void recordUsersCalls(HttpState httpState) {
        // two structurally-identical recorded calls /users/1 and /users/2, as if proxied + recorded
        httpState.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("/users/1").withMethod("GET"))
                .setHttpResponse(response("ok"))
                .setExpectation(new Expectation(request("/users/1").withMethod("GET"), Times.once(), TimeToLive.unlimited(), 0).withId("key_one").thenRespond(response("ok")))
        );
        httpState.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("/users/2").withMethod("GET"))
                .setHttpResponse(response("ok"))
                .setExpectation(new Expectation(request("/users/2").withMethod("GET"), Times.once(), TimeToLive.unlimited(), 0).withId("key_two").thenRespond(response("ok")))
        );
    }

    private HttpResponse retrieveRecorded(HttpState httpState, String format, String forwardUnmatchedTo) {
        FakeResponseWriter responseWriter = new FakeResponseWriter();
        HttpRequest retrieveRequest = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
            .withQueryStringParameter("format", format)
            .withBody(requestDefinitionSerializer.serialize(request()));
        if (forwardUnmatchedTo != null) {
            retrieveRequest.withQueryStringParameter("forwardUnmatchedTo", forwardUnmatchedTo);
        }
        boolean handle = httpState.handle(retrieveRequest, responseWriter, false);
        assertThat(handle, is(true));
        return responseWriter.response;
    }

    // ---- arming record-and-forward ----

    @Test
    public void shouldArmRecordAndForwardWhenUpstreamSupplied() {
        // given - no proxy configured (host defaults to empty string, port to null)
        Configuration configuration = configuration();
        assertThat(configuration.proxyRemoteHost(), is(""));
        assertThat(configuration.proxyRemotePort(), nullValue());
        HttpState httpState = newHttpState(configuration);

        // when - retrieve with forwardUnmatchedTo supplied
        HttpResponse response = retrieveRecorded(httpState, "JSON", "upstream.example.com:9090");

        // then - record-and-forward is armed for subsequent traffic
        assertThat(response.getStatusCode(), is(200));
        assertThat(configuration.proxyRemoteHost(), is("upstream.example.com"));
        assertThat(configuration.proxyRemotePort(), is(9090));
        assertThat(configuration.attemptToProxyIfNoMatchingExpectation(), is(true));
    }

    @Test
    public void shouldAcceptFullUrlUpstreamWithSchemeAndDefaultPort() {
        // given
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);

        // when - URL form with https scheme and no explicit port
        HttpResponse response = retrieveRecorded(httpState, "JSON", "https://api.example.com");

        // then - default https port is derived
        assertThat(response.getStatusCode(), is(200));
        assertThat(configuration.proxyRemoteHost(), is("api.example.com"));
        assertThat(configuration.proxyRemotePort(), is(443));
        assertThat(configuration.attemptToProxyIfNoMatchingExpectation(), is(true));
    }

    @Test
    public void shouldDefaultToPortEightyForBareHost() {
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);

        HttpResponse response = retrieveRecorded(httpState, "JSON", "api.example.com");

        assertThat(response.getStatusCode(), is(200));
        assertThat(configuration.proxyRemoteHost(), is("api.example.com"));
        assertThat(configuration.proxyRemotePort(), is(80));
    }

    @Test
    public void shouldNotArmRecordAndForwardWhenUpstreamAbsent() {
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);

        HttpResponse response = retrieveRecorded(httpState, "JSON", null);

        assertThat(response.getStatusCode(), is(200));
        // upstream not armed: host stays at its (empty) default and port unset
        assertThat(configuration.proxyRemoteHost(), is(""));
        assertThat(configuration.proxyRemotePort(), nullValue());
    }

    // ---- validation ----

    @Test
    public void shouldReturnBadRequestForBlankHostUpstream() {
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);

        HttpResponse response = retrieveRecorded(httpState, "JSON", "://:8080");

        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("forwardUnmatchedTo"));
        // state untouched
        assertThat(configuration.proxyRemoteHost(), is(""));
        assertThat(configuration.proxyRemotePort(), nullValue());
    }

    @Test
    public void shouldBlockUpstreamRejectedBySsrfPolicyWithoutMutatingState() {
        // given - SSRF policy enabled; loopback is blocked
        Configuration configuration = configuration().forwardProxyBlockPrivateNetworks(true);
        HttpState httpState = newHttpState(configuration);

        // when - attempt to arm forwarding to loopback
        HttpResponse response = retrieveRecorded(httpState, "JSON", "127.0.0.1:8080");

        // then - blocked with 403 and no state mutated (fail-closed before connect)
        assertThat(response.getStatusCode(), is(403));
        assertThat(response.getBodyAsString(), containsString("SSRF"));
        assertThat(configuration.proxyRemoteHost(), is(""));
        assertThat(configuration.proxyRemotePort(), nullValue());
    }

    // ---- authorization: arming changes server state, so it is not a read ----

    private void authenticateAs(HttpState httpState, Set<String> scopes) {
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
    }

    private Configuration authorizationEnabledConfiguration() {
        Map<String, ControlPlaneRole> mapping = new LinkedHashMap<>();
        mapping.put("qa-team", ControlPlaneRole.MUTATE);
        mapping.put("viewers", ControlPlaneRole.READ);
        return configuration().controlPlaneAuthorizationEnabled(true).controlPlaneScopeMapping(mapping);
    }

    @Test
    public void shouldForbidAReadOnlyPrincipalFromArmingRecordAndForward() {
        // given - a principal granted only the READ role
        Configuration configuration = authorizationEnabledConfiguration();
        HttpState httpState = newHttpState(configuration);
        authenticateAs(httpState, Set.of("viewers"));

        // when - it tries to arm forwarding with forwardUnmatchedTo
        HttpResponse response = retrieveRecorded(httpState, "JSON", "upstream.example.com:9090");

        // then - refused, and nothing was armed
        assertThat(response.getStatusCode(), is(403));
        assertThat(configuration.proxyRemoteHost(), is(""));
        assertThat(configuration.proxyRemotePort(), nullValue());

        // and - a retrieve without forwardUnmatchedTo is still a read it may make
        assertThat(retrieveRecorded(httpState, "JSON", null).getStatusCode(), is(200));
    }

    @Test
    public void shouldLetAPrincipalThatMayChangeStateArmRecordAndForward() {
        // given - a principal granted the MUTATE role
        Configuration configuration = authorizationEnabledConfiguration();
        HttpState httpState = newHttpState(configuration);
        authenticateAs(httpState, Set.of("qa-team"));

        // when
        HttpResponse response = retrieveRecorded(httpState, "JSON", "upstream.example.com:9090");

        // then
        assertThat(response.getStatusCode(), is(200));
        assertThat(configuration.proxyRemoteHost(), is("upstream.example.com"));
        assertThat(configuration.proxyRemotePort(), is(9090));
    }

    // ---- full round-trip: arm + record -> retrieve as code/JSON -> re-import ----

    @Test
    public void shouldArmThenReturnRecordedExpectationsAsValidJava() {
        // given - arm record-and-forward, then traffic is recorded
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);
        retrieveRecorded(httpState, "JAVA", "upstream.example.com:9090"); // arm
        recordUsersCalls(httpState); // traffic-driven recording

        // when - the next retrieve returns recorded expectations as Java code
        HttpResponse response = retrieveRecorded(httpState, "JAVA", null);

        // then - valid, ready-to-use Java code referencing the recorded calls
        assertThat(response.getStatusCode(), is(200));
        String java = response.getBodyAsString();
        assertThat(java, containsString("new MockServerClient"));
        assertThat(java, containsString("/users/1"));
        assertThat(java, containsString("/users/2"));
    }

    @Test
    public void shouldRoundTripRecordedExpectationsThroughJsonAndReImportToMatchingExpectations() {
        // given - arm + record
        Configuration configuration = configuration();
        HttpState httpState = newHttpState(configuration);
        retrieveRecorded(httpState, "JSON", "upstream.example.com:9090");
        recordUsersCalls(httpState);

        // when - retrieve recorded expectations as JSON
        HttpResponse response = retrieveRecorded(httpState, "JSON", null);
        assertThat(response.getStatusCode(), is(200));
        String json = response.getBodyAsString();

        // then - JSON re-imports to expectations that match the recorded requests (replayable)
        Expectation[] reimported = expectationSerializer.deserializeArray(json, true);
        assertThat(reimported.length, is(2));
        // each recorded request matches its re-imported expectation
        assertThat(((HttpRequest) reimported[0].getHttpRequest()).getPath().getValue(), is("/users/1"));
        assertThat(((HttpRequest) reimported[1].getHttpRequest()).getPath().getValue(), is("/users/2"));
    }
}
