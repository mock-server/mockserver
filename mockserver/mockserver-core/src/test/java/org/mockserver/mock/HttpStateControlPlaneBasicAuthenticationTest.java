package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.authentication.basic.PasswordHash;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.configuration.Configuration;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.audit.AuditEntry;
import org.mockserver.mock.audit.AuditStore;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.test.TempFileWriter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Control-plane HTTP Basic authentication end to end through {@link HttpState#handle}: the verified user is the
 * audited principal, its configured scopes feed the authorization policy, and a refused caller is challenged.
 *
 * <p>Drives the process-wide {@link AuditStore}, so it runs in the sequential Surefire phase.
 */
public class HttpStateControlPlaneBasicAuthenticationTest {

    private static final String ADMIN_HASH = PasswordHash.pbkdf2Sha256("admin-pass", 10_000);
    private static final String VIEWER_HASH = PasswordHash.pbkdf2Sha256("viewer-pass", 10_000);

    private HttpState httpState;

    private static class CapturingResponseWriter extends ResponseWriter {
        private HttpResponse response;

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    private void start(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateControlPlaneBasicAuthenticationTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
    }

    private static Configuration basicConfiguration() {
        Map<String, ControlPlaneRole> mapping = new LinkedHashMap<>();
        mapping.put("platform-admins", ControlPlaneRole.ADMIN);
        mapping.put("viewers", ControlPlaneRole.READ);
        return configuration()
            .controlPlaneAuditEnabled(true)
            .controlPlaneAuditReads(true)
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + ADMIN_HASH + ":platform-admins;viewer:" + VIEWER_HASH + ":viewers")
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(mapping);
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpRequest putExpectation(String authorization) {
        HttpRequest request = request("/mockserver/expectation").withMethod("PUT").withSecure(true).withRemoteAddress("10.0.0.5:5555")
            .withBody("[{\"httpRequest\":{\"path\":\"/x\"},\"httpResponse\":{\"statusCode\":200}}]");
        return authorization != null ? request.withHeader("Authorization", authorization) : request;
    }

    private HttpResponse handle(HttpRequest request) {
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        httpState.handle(request, responseWriter, false);
        return responseWriter.response;
    }

    private static AuditEntry lastAudit() {
        return AuditStore.getInstance().getRecent(1).get(0);
    }

    @Before
    public void clearAudit() {
        AuditStore.getInstance().clear();
    }

    @After
    public void stop() {
        AuditStore.getInstance().clear();
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void shouldAuditTheVerifiedBasicUserAsThePrincipal() {
        start(basicConfiguration());

        HttpResponse response = handle(putExpectation(basic("admin", "admin-pass")));

        assertThat(response.getStatusCode(), is(201));
        assertThat(lastAudit().getPrincipal(), is("admin"));
        assertThat(lastAudit().getPrincipalSource(), is("verified-basic"));
        assertThat(lastAudit().getOutcome(), is("AUTHORIZED"));
    }

    @Test
    public void shouldAuthorizeTheBasicUserByItsConfiguredScopes() {
        start(basicConfiguration());

        assertThat(handle(putExpectation(basic("viewer", "viewer-pass"))).getStatusCode(), is(403));
        assertThat(lastAudit().getPrincipal(), is("viewer"));
        assertThat(lastAudit().getOutcome(), is("FORBIDDEN"));

        HttpResponse retrieve = handle(request("/mockserver/retrieve").withMethod("PUT").withSecure(true).withHeader("Authorization", basic("viewer", "viewer-pass")));
        assertThat(retrieve.getStatusCode(), is(200));
    }

    @Test
    public void shouldChallengeAndRefuseWrongOrMissingCredentials() {
        start(basicConfiguration());

        HttpResponse wrong = handle(putExpectation(basic("admin", "guess")));
        assertThat(wrong.getStatusCode(), is(401));
        assertThat(wrong.getFirstHeader("WWW-Authenticate"), is("Basic realm=\"MockServer control plane\", charset=\"UTF-8\""));
        assertThat(wrong.getBodyAsString(), is("Unauthorized for control plane - control plane request failed basic authentication"));

        HttpResponse missing = handle(putExpectation(null));
        assertThat(missing.getStatusCode(), is(401));
        assertThat(missing.getFirstHeader("WWW-Authenticate"), containsString("Basic"));
        assertThat("a refused request changes nothing", AuditStore.getInstance().getRecent(10), is(empty()));
    }

    @Test
    public void shouldRefuseBasicCredentialsSentWithoutTls() {
        start(basicConfiguration());

        HttpResponse plainText = handle(putExpectation(basic("admin", "admin-pass")).withSecure(false));

        assertThat(plainText.getStatusCode(), is(401));
        assertThat(plainText.getBodyAsString(), is("Unauthorized for control plane - basic authentication requires TLS"));
        assertThat("a browser on plain HTTP must not be invited to send a password", plainText.getFirstHeader("WWW-Authenticate"), is(""));
        assertThat(handle(putExpectation(null).withSecure(false)).getFirstHeader("WWW-Authenticate"), is(""));
    }

    @Test
    public void shouldChallengeOnPlainTextOnlyWhenInsecureIsAllowedForTests() {
        start(basicConfiguration().controlPlaneBasicAuthenticationAllowInsecure(true));

        assertThat(handle(putExpectation(null).withSecure(false)).getFirstHeader("WWW-Authenticate"), containsString("Basic"));
    }

    @Test
    public void shouldNotLogTheCredentialsOfAFailedOrThrottledAttempt() {
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256)));
        Configuration configuration = basicConfiguration().controlPlaneJWTAuthenticationRequired(true).controlPlaneJWTAuthenticationJWKSource(jwkFile);
        java.util.List<String> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        MockServerLogger capturing = new MockServerLogger(configuration, HttpStateControlPlaneBasicAuthenticationTest.class) {
            @Override
            public boolean isEnabledForInstance(org.slf4j.event.Level level) {
                // the default log level: TRACE request dumps are governed by redactSecretsInLog instead
                return level.toInt() >= org.slf4j.event.Level.INFO.toInt();
            }

            @Override
            public void logEvent(org.mockserver.log.model.LogEntry logEntry) {
                if (logEntry.getLogLevel() == null || logEntry.getLogLevel().toInt() < org.slf4j.event.Level.INFO.toInt()) {
                    return;
                }
                logged.add(logEntry.getMessage() + " " + java.util.Arrays.toString(logEntry.getHttpRequests()) + " " + java.util.Arrays.toString(logEntry.getArguments()));
            }
        };
        httpState = new HttpState(configuration, capturing, new Scheduler(configuration, capturing, true));

        for (int i = 0; i < 6; i++) {
            assertThat(handle(putExpectation(basic("admin", "near-miss-" + i))).getStatusCode(), is(401));
        }
        HttpResponse throttled = handle(putExpectation(basic("admin", "admin-pass")));
        assertThat(throttled.getBodyAsString(), containsString("too many failed basic authentication attempts"));

        assertThat(logged.isEmpty(), is(false));
        for (String entry : logged) {
            assertThat(entry, org.hamcrest.Matchers.not(containsString("near-miss")));
            assertThat(entry, org.hamcrest.Matchers.not(containsString("admin-pass")));
            assertThat(entry, org.hamcrest.Matchers.not(containsString(basic("admin", "admin-pass").substring("Basic ".length()))));
            assertThat(entry, org.hamcrest.Matchers.not(containsString(basic("admin", "near-miss-0").substring("Basic ".length()))));
        }
    }

    @Test
    public void shouldAcceptBasicCredentialsWithoutTlsWhenInsecureIsAllowedForTests() {
        start(basicConfiguration().controlPlaneBasicAuthenticationAllowInsecure(true));

        assertThat(handle(putExpectation(basic("admin", "admin-pass")).withSecure(false)).getStatusCode(), is(201));
    }

    private HttpResponse handleAndWait(HttpRequest request) throws InterruptedException {
        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        httpState.handle(request, responseWriter, false);
        long deadline = System.currentTimeMillis() + 30_000;
        while (responseWriter.response == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return responseWriter.response;
    }

    private static HttpRequest retrieveLogEntries(String authorization) {
        return request("/mockserver/retrieve").withMethod("PUT").withSecure(true).withRemoteAddress("10.0.0.9:5555")
            .withQueryStringParameter("type", "LOGS").withQueryStringParameter("format", "LOG_ENTRIES")
            .withHeader("Authorization", authorization);
    }

    /**
     * The reviewer's probe: an admin makes control-plane calls, then a READ-role viewer retrieves the logs.
     *
     * @return the retrieved log entries
     */
    private String adminCallsThenViewerRetrievesLogs(String logLevel) throws Exception {
        if (httpState != null) {
            httpState.stop();
        }
        AuditStore.getInstance().clear();
        start(basicConfiguration().logLevel(logLevel));
        String admin = basic("admin", "admin-pass");

        assertThat(handle(request("/mockserver/clock").withMethod("PUT").withSecure(true).withRemoteAddress("10.0.0.5:5555")
            .withHeader("Authorization", admin).withBody("{\"action\":\"reset\"}")).getStatusCode(), is(200));
        assertThat(handle(putExpectation(admin)).getStatusCode(), is(201));
        assertThat(handle(request("/mockserver/scenario/log-redaction").withMethod("PUT").withSecure(true).withHeader("Authorization", admin)
            .withBody("{\"state\":\"Started\"}")).getStatusCode(), is(200));

        HttpResponse logs = handleAndWait(retrieveLogEntries(basic("viewer", "viewer-pass")));
        assertThat(logs.getStatusCode(), is(200));
        String body = logs.getBodyAsString();
        assertThat(logLevel, body, org.hamcrest.Matchers.not(containsString(admin.substring("Basic ".length()))));
        assertThat(logLevel, body, org.hamcrest.Matchers.not(containsString("admin-pass")));
        assertThat(logLevel, body, org.hamcrest.Matchers.not(containsString(basic("viewer", "viewer-pass").substring("Basic ".length()))));
        return body;
    }

    @Test
    public void shouldNotLetAReadRoleUserReadAnAdminsPasswordFromTheLogs() throws Exception {
        String body = adminCallsThenViewerRetrievesLogs("INFO");

        assertThat(body, containsString("/mockserver/clock"));
        assertThat(body, containsString("/mockserver/scenario/log-redaction"));
    }

    @Test
    public void shouldNotLogControlPlaneCredentialsAtAnyLogLevelOnAnyRun() throws Exception {
        for (String logLevel : java.util.Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR")) {
            for (int run = 0; run < 3; run++) {
                String body = adminCallsThenViewerRetrievesLogs(logLevel);
                if ("TRACE".equals(logLevel)) {
                    assertThat("the pre-gate received-request entry is present, and masked", body, containsString("received request"));
                    assertThat(body, containsString("***REDACTED***"));
                }
            }
        }
    }

    @Test
    public void shouldMaskAControlPlaneRequestLoggedByAnySiteOnRetrieve() throws Exception {
        start(basicConfiguration().logLevel("INFO"));
        String admin = basic("admin", "admin-pass");
        HttpRequest neverThroughTheGate = request("/mockserver/expectation").withMethod("PUT").withHeader("Authorization", admin);
        httpState.log(new org.mockserver.log.model.LogEntry()
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(neverThroughTheGate)
            .setMessageFormat("a future log site:{}")
            .setArguments(neverThroughTheGate));

        String logs = handleAndWait(retrieveLogEntries(basic("viewer", "viewer-pass"))).getBodyAsString();

        assertThat(logs, containsString("a future log site"));
        assertThat(logs, org.hamcrest.Matchers.not(containsString(admin.substring("Basic ".length()))));
    }

    @Test
    public void shouldMaskTheLivenessPathAtTrace() throws Exception {
        start(basicConfiguration().logLevel("TRACE").livenessHttpGetPath("/health"));
        String admin = basic("admin", "admin-pass");
        handle(request("/health").withMethod("GET").withHeader("Authorization", admin));

        String logs = handleAndWait(retrieveLogEntries(basic("viewer", "viewer-pass"))).getBodyAsString();

        assertThat(logs, containsString("/health"));
        assertThat(logs, org.hamcrest.Matchers.not(containsString(admin.substring("Basic ".length()))));
    }

    @Test
    public void shouldNotLogAControlPlaneBearerToken() throws Exception {
        org.mockserver.keys.AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(keyPair));
        String jwt = new org.mockserver.authentication.jwt.JWTGenerator(keyPair).generateJWT();
        start(configuration().logLevel("INFO").controlPlaneJWTAuthenticationRequired(true).controlPlaneJWTAuthenticationJWKSource(jwkFile));

        assertThat(handle(request("/mockserver/clock").withMethod("PUT").withHeader("Authorization", "Bearer " + jwt).withBody("{\"action\":\"reset\"}")).getStatusCode(), is(200));
        HttpResponse logs = handleAndWait(retrieveLogEntries("Bearer " + jwt));

        assertThat(logs.getBodyAsString(), containsString("/mockserver/clock"));
        assertThat(logs.getBodyAsString(), org.hamcrest.Matchers.not(containsString(jwt)));
    }

    @Test
    public void shouldStillLogDataPlaneCredentialsAsSentForVerification() throws Exception {
        start(basicConfiguration().logLevel("INFO"));
        HttpRequest dataPlane = request("/api/thing").withHeader("Authorization", "Bearer data-plane-token");

        CapturingResponseWriter responseWriter = new CapturingResponseWriter();
        assertThat("a data-plane request is not handled by the control plane", httpState.handle(dataPlane, responseWriter, false), is(false));
        httpState.log(new org.mockserver.log.model.LogEntry()
            .setType(org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST)
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(dataPlane)
            .setMessageFormat("received request:{}")
            .setArguments(dataPlane));

        assertThat(dataPlane.isControlPlaneRequest(), is(false));
        java.util.concurrent.CompletableFuture<java.util.List<org.mockserver.log.model.LogEntry>> logged = new java.util.concurrent.CompletableFuture<>();
        httpState.getMockServerLog().retrieveRequestLogEntries(null, logged::complete);
        assertThat(java.util.Arrays.toString(logged.get(30, java.util.concurrent.TimeUnit.SECONDS).get(0).getHttpRequests()), containsString("data-plane-token"));
    }

    @Test
    public void shouldNotChallengeForBasicWhenBasicIsNotConfigured() {
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256)));
        start(configuration().controlPlaneJWTAuthenticationRequired(true).controlPlaneJWTAuthenticationJWKSource(jwkFile));

        HttpResponse refused = handle(putExpectation(null));

        assertThat(refused.getStatusCode(), is(401));
        assertThat(refused.getFirstHeader("WWW-Authenticate"), is(""));
    }
}
