package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.audit.AuditEntry;
import org.mockserver.mock.audit.AuditStore;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Behavioural tests for control-plane audit logging fired from
 * {@link HttpState#controlPlaneRequestAuthenticated}: mutations are recorded when
 * enabled, nothing is recorded when disabled, reads are skipped by default, secrets
 * are never stored, reset clears the log, and the JWT principal is best-effort.
 *
 * <p>State-mutating: drives the process-wide {@link AuditStore} singleton, so it must
 * run in the sequential Surefire phase.
 */
public class HttpStateAuditTest {

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

    private void rebuild(boolean auditEnabled, boolean auditReads) {
        Configuration configuration = configuration()
            .controlPlaneAuditEnabled(auditEnabled)
            .controlPlaneAuditReads(auditReads);
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, HttpStateAuditTest.class), true);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, HttpStateAuditTest.class), scheduler);
    }

    @Before
    public void setUp() {
        AuditStore.getInstance().clear();
        rebuild(true, false);
    }

    @After
    public void tearDown() {
        AuditStore.getInstance().clear();
        if (httpState != null) {
            httpState.stop();
        }
    }

    private void handle(HttpRequest request) {
        FakeResponseWriter rw = new FakeResponseWriter();
        httpState.handle(request, rw, false);
    }

    private static String unsignedJwt(String sub) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    @Test
    public void mutationRecordsOneEntryWithCorrectFields() {
        handle(request("/mockserver/expectation").withMethod("PUT").withRemoteAddress("10.0.0.5:5555")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        AuditEntry entry = entries.get(0);
        assertThat(entry.getMethod(), is("PUT"));
        assertThat(entry.getOperation(), is("expectation"));
        assertThat(entry.getSourceAddress(), is("10.0.0.5:5555"));
        assertThat(entry.getOutcome(), is("AUTHORIZED"));
        assertThat(entry.getPrincipal(), is("anonymous"));
        assertThat(entry.getPrincipalSource(), is("none"));
    }

    @Test
    public void disabledRecordsNothing() {
        rebuild(false, false);
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));
        assertThat(AuditStore.getInstance().size(), is(0));
    }

    @Test
    public void readsNotAuditedByDefault() {
        // GET (a read) and PUT /retrieve (a known read PUT) must not be audited by default
        handle(request("/mockserver/retrieve").withMethod("PUT").withBody("{}"));
        handle(request("/mockserver/drift").withMethod("GET"));
        assertThat(AuditStore.getInstance().size(), is(0));
    }

    public static class AuditedAdminExtension implements org.mockserver.extension.AdminApiExtension {
        @Override
        public String name() {
            return "audited";
        }

        @Override
        public HttpResponse handle(HttpRequest request, String subPath) {
            return HttpResponse.response().withStatusCode(200);
        }

        @Override
        public boolean isReadOnly(String method, String subPath) {
            return subPath.equals("/status");
        }
    }

    @Test
    public void auditsAnExtensionRouteByItsOwnReadOrMutateClassification() {
        httpState.stop();
        Configuration configuration = configuration().controlPlaneAuditEnabled(true).extensions(AuditedAdminExtension.class);
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateAuditTest.class);
        org.mockserver.extension.ExtensionRegistry registry = org.mockserver.extension.ExtensionRegistry.start(configuration, logger);
        try {
            httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true), registry);

            // a GET that the extension does not declare read-only changes state, so it is audited
            httpState.handle(request("/mockserver/extensions/audited/reload").withMethod("GET"), new FakeResponseWriter(), true);
            // a route the extension declares read-only is a read, which is not audited by default
            httpState.handle(request("/mockserver/extensions/audited/status").withMethod("GET"), new FakeResponseWriter(), true);

            List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
            assertThat(entries.size(), is(1));
            assertThat(entries.get(0).getMethod(), is("GET"));
            assertThat(entries.get(0).getOperation(), is("extensions/audited/reload"));
            assertThat(entries.get(0).getOutcome(), is("AUTHORIZED"));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void refusesAnExtensionRouteWhoseIsReadOnlyThrowsAnErrorInAuditOnlyMode() {
        httpState.stop();
        java.util.concurrent.atomic.AtomicInteger handled = new java.util.concurrent.atomic.AtomicInteger();
        org.mockserver.extension.AdminApiExtension failing = new org.mockserver.extension.AdminApiExtension() {
            @Override
            public String name() {
                return "failing";
            }

            @Override
            public HttpResponse handle(HttpRequest request, String subPath) {
                handled.incrementAndGet();
                return HttpResponse.response().withStatusCode(200);
            }

            @Override
            public boolean isReadOnly(String method, String subPath) {
                throw new AssertionError("classifier broke");
            }
        };
        // authorization off and audit on, so only the audit asks isReadOnly, and the audit swallows what it throws
        Configuration configuration = configuration().controlPlaneAuditEnabled(true)
            .extensions((java.util.function.Supplier<org.mockserver.extension.Extension>) () -> failing);
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateAuditTest.class);
        org.mockserver.extension.ExtensionRegistry registry = org.mockserver.extension.ExtensionRegistry.start(configuration, logger);
        try {
            httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true), registry);
            FakeResponseWriter responseWriter = new FakeResponseWriter();

            httpState.handle(request("/mockserver/extensions/failing/x").withMethod("PUT"), responseWriter, true);

            assertThat(responseWriter.response.getStatusCode(), is(500));
            assertThat(handled.get(), is(0));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void readsAuditedWhenReadsEnabled() {
        rebuild(true, true);
        handle(request("/mockserver/retrieve").withMethod("PUT").withBody("{}"));
        assertThat(AuditStore.getInstance().size(), is(1));
        assertThat(AuditStore.getInstance().getRecent(1).get(0).getOperation(), is("retrieve"));
    }

    @Test
    public void secretsAreNeverStored() {
        String secret = "super-secret-token-value";
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withHeader("Authorization", "Bearer " + secret)
            .withHeader("x-api-key", secret)
            .withPath("/mockserver/expectation")
            .withQueryStringParameter("token", secret)
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        AuditEntry entry = entries.get(0);
        // No field may contain the secret
        assertThat(entry.getPath(), not(containsString(secret)));
        assertThat(String.valueOf(entry.getSummary()), not(containsString(secret)));
        assertThat(entry.getPrincipal(), not(containsString(secret)));
        assertThat(entry.getOperation(), not(containsString(secret)));
        // Path must have no query string
        assertThat(entry.getPath(), not(containsString("?")));
        assertThat(entry.getPath(), not(containsString("token")));
    }

    @Test
    public void resetClearsAudit() {
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));
        assertThat(AuditStore.getInstance().size(), is(1));
        httpState.reset();
        assertThat(AuditStore.getInstance().size(), is(0));
    }

    @Test
    public void resetRequestClearsEarlierEntriesButKeepsTheRecordOfTheReset() {
        handle(request("/mockserver/expectation").withMethod("PUT").withRemoteAddress("10.0.0.7:7777")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));
        handle(request("/mockserver/reset").withMethod("PUT").withRemoteAddress("10.0.0.8:8888"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        assertThat(entries.get(0).getOperation(), is("reset"));
        assertThat(entries.get(0).getSourceAddress(), is("10.0.0.8:8888"));
        assertThat(entries.get(0).getOutcome(), is("AUTHORIZED"));
    }

    @Test
    public void resetWithAuditOffDoesNotReAddAnEarlierThreadsResetRecord() {
        handle(request("/mockserver/reset").withMethod("PUT"));
        assertThat(AuditStore.getInstance().size(), is(1));
        rebuild(false, false);

        handle(request("/mockserver/reset").withMethod("PUT"));

        assertThat(AuditStore.getInstance().size(), is(0));
    }

    @Test
    public void entriesCarryASummaryOfTheOperation() {
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));
        handle(request("/mockserver/chaosExperiment").withMethod("DELETE"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.get(1).getSummary(), is("Created or updated expectations"));
        assertThat(entries.get(0).getSummary(), is("Deleted chaosExperiment"));
    }

    @Test
    public void verifiedOidcResultRecordsVerifiedPrincipalAndSource() {
        // a handler that returns a VERIFIED principal must override the best-effort extraction
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated("service-account-ci", "verified-oidc", Map.of("sub", "service-account-ci"), Set.of("mockserver.write"));
            }
        });

        // include an unverified bearer token with a DIFFERENT sub to prove the verified principal wins
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withHeader("Authorization", "Bearer " + unsignedJwt("mallory"))
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        assertThat(entries.get(0).getPrincipal(), is("service-account-ci"));
        assertThat(entries.get(0).getPrincipalSource(), is("verified-oidc"));
    }

    @Test
    public void legacyBooleanHandlerFallsBackToBestEffortPrincipal() {
        // a handler implementing ONLY the boolean SPI authenticates via the default adapter
        // (no verified principal) and audit must fall back to best-effort extraction
        httpState.setControlPlaneAuthenticationHandler(request -> true);

        handle(request("/mockserver/expectation").withMethod("PUT")
            .withHeader("Authorization", "Bearer " + unsignedJwt("alice"))
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        assertThat(entries.get(0).getPrincipal(), is("alice"));
        assertThat(entries.get(0).getPrincipalSource(), is("jwt"));
    }

    @Test
    public void principalBestEffortFromJwt() {
        handle(request("/mockserver/expectation").withMethod("PUT")
            .withHeader("Authorization", "Bearer " + unsignedJwt("alice"))
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]"));

        List<AuditEntry> entries = AuditStore.getInstance().getRecent(10);
        assertThat(entries.size(), is(1));
        assertThat(entries.get(0).getPrincipal(), is("alice"));
        assertThat(entries.get(0).getPrincipalSource(), is("jwt"));
    }
}
