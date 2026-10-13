package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.audit.AuditEntry;
import org.mockserver.mock.audit.AuditStore;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Behavioural tests for coarse control-plane authorization (claims/groups -> read/mutate/
 * admin roles), enforced in {@link HttpState#controlPlaneRequestAuthenticated} after a
 * principal is authenticated. Drives {@link HttpState#handle} with a stubbed
 * {@link AuthenticationHandler} that returns an {@link AuthenticationResult} carrying
 * given verified scopes.
 *
 * <p>State-mutating: constructs {@link HttpState} and drives the process-wide
 * {@link AuditStore} singleton, so it must run in the sequential Surefire phase.
 */
public class HttpStateAuthorizationTest {

    private HttpState httpState;

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

    private static Map<String, ControlPlaneRole> mapping() {
        Map<String, ControlPlaneRole> map = new LinkedHashMap<>();
        map.put("platform-admins", ControlPlaneRole.ADMIN);
        map.put("qa-team", ControlPlaneRole.MUTATE);
        map.put("viewers", ControlPlaneRole.READ);
        return map;
    }

    private void rebuild(boolean authorizationEnabled) {
        Configuration configuration = configuration()
            .controlPlaneAuditEnabled(true)
            .controlPlaneAuditReads(false)
            .controlPlaneAuthorizationEnabled(authorizationEnabled)
            .controlPlaneScopeMapping(mapping());
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, HttpStateAuthorizationTest.class), true);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, HttpStateAuthorizationTest.class), scheduler);
    }

    private void authenticateWithScopes(String principal, Set<String> scopes) {
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated(principal, "verified-oidc", Map.of("sub", principal), scopes);
            }
        });
    }

    @Before
    public void setUp() {
        AuditStore.getInstance().clear();
        rebuild(true);
    }

    @After
    public void tearDown() {
        AuditStore.getInstance().clear();
        if (httpState != null) {
            httpState.stop();
        }
    }

    private HttpResponse handle(HttpRequest request) {
        FakeResponseWriter rw = new FakeResponseWriter();
        httpState.handle(request, rw, false);
        return rw.response;
    }

    private static HttpRequest putExpectation() {
        return request("/mockserver/expectation").withMethod("PUT").withRemoteAddress("10.0.0.5:5555")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]");
    }

    private static HttpRequest retrieve() {
        return request("/mockserver/retrieve").withMethod("PUT").withRemoteAddress("10.0.0.5:5555").withBody("{}");
    }

    @Test
    public void aForbiddenResetIsNeverReAddedByALaterReset() {
        authenticateWithScopes("viewer", Set.of("viewers"));
        assertThat(handle(request("/mockserver/reset").withMethod("PUT")).getStatusCode(), is(403));
        assertThat(AuditStore.getInstance().getRecent(1).get(0).getOutcome(), is("FORBIDDEN"));
        AuditStore.getInstance().clear();
        Configuration auditOff = configuration().controlPlaneAuditEnabled(false);
        httpState.stop();
        httpState = new HttpState(auditOff, new MockServerLogger(auditOff, HttpStateAuthorizationTest.class), new Scheduler(auditOff, new MockServerLogger(auditOff, HttpStateAuthorizationTest.class), true));

        assertThat(handle(request("/mockserver/reset").withMethod("PUT")).getStatusCode(), is(200));

        assertThat(AuditStore.getInstance().size(), is(0));
    }

    @Test
    public void mutateRolePrincipalCanMutate() {
        authenticateWithScopes("qa", Set.of("qa-team"));
        HttpResponse response = handle(putExpectation());
        assertThat(response.getStatusCode(), is(201));
    }

    @Test
    public void adminRolePrincipalCanMutateAndRead() {
        authenticateWithScopes("root", Set.of("platform-admins"));
        assertThat(handle(putExpectation()).getStatusCode(), is(201));
        // a read also passes for admin
        assertThat(handle(retrieve()).getStatusCode(), is(200));
    }

    @Test
    public void readOnlyPrincipalCanReadButIsForbiddenOnMutation() {
        authenticateWithScopes("viewer", Set.of("viewers"));
        // read passes
        assertThat(handle(retrieve()).getStatusCode(), is(200));
        // mutation is forbidden
        HttpResponse response = handle(putExpectation());
        assertThat(response.getStatusCode(), is(403));
        // and the denial is audited with outcome FORBIDDEN
        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        AuditEntry forbidden = entries.stream().filter(e -> "FORBIDDEN".equals(e.getOutcome())).findFirst().orElse(null);
        assertThat(forbidden != null, is(true));
        assertThat(forbidden.getOperation(), is("expectation"));
        assertThat(forbidden.getPrincipal(), is("viewer"));
    }

    @Test
    public void readOnlyPrincipalCanLintExpectationsItSendsBecauseLintStoresNothing() {
        authenticateWithScopes("viewer", Set.of("viewers"));
        HttpRequest lint = request("/mockserver/lint").withMethod("PUT").withRemoteAddress("10.0.0.5:5555")
            .withBody("[{\"httpRequest\":{\"path\":\"x\"},\"httpResponse\":{\"statusCode\":200}}]");

        assertThat(handle(lint).getStatusCode(), is(200));
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null).size(), is(0));
    }

    @Test
    public void principalWithNoMappedRoleIsForbiddenOnMutation() {
        authenticateWithScopes("nobody", Set.of("unmapped-group"));
        HttpResponse response = handle(putExpectation());
        assertThat(response.getStatusCode(), is(403));
        AuditEntry entry = AuditStore.getInstance().getRecent(10).get(0);
        assertThat(entry.getOutcome(), is("FORBIDDEN"));
    }

    @Test
    public void principalWithNoScopesIsForbiddenFailClosed() {
        authenticateWithScopes("empty", Set.of());
        assertThat(handle(putExpectation()).getStatusCode(), is(403));
    }

    @Test
    public void authorizationDisabledDoesNotForbid() {
        // Wave-1 behaviour: with authorization disabled, an authenticated read-only
        // principal can still mutate (authn only, no authz)
        rebuild(false);
        authenticateWithScopes("viewer", Set.of("viewers"));
        HttpResponse response = handle(putExpectation());
        assertThat(response.getStatusCode(), is(201));
        // outcome recorded is AUTHORIZED, never FORBIDDEN
        assertThat(AuditStore.getInstance().getRecent(1).get(0).getOutcome(), is("AUTHORIZED"));
    }

    @Test
    public void authorizedMutationRecordsAuthorizedOutcome() {
        authenticateWithScopes("qa", Set.of("qa-team"));
        handle(putExpectation());
        AuditEntry entry = AuditStore.getInstance().getRecent(1).get(0);
        assertThat(entry.getOutcome(), is("AUTHORIZED"));
        assertThat(entry.getPrincipal(), is("qa"));
        assertThat(entry.getPrincipalSource(), is("verified-oidc"));
    }
}
