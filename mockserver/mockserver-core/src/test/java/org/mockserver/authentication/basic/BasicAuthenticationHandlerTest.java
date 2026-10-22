package org.mockserver.authentication.basic;

import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

public class BasicAuthenticationHandlerTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(BasicAuthenticationHandlerTest.class);
    private static final String ADMIN_HASH = PasswordHash.pbkdf2Sha256("correct horse", 10_000);
    private static final String VIEWER_HASH = PasswordHash.bcrypt("viewer-pass", 4);
    private static final BasicAuthenticationCredentials CREDENTIALS = BasicAuthenticationCredentials.parse(
        "admin:" + ADMIN_HASH + ":platform-admins qa-team;viewer:" + VIEWER_HASH, null
    );

    private final AtomicLong now = new AtomicLong(1_000_000);

    private BasicAuthenticationHandler handler(boolean allowInsecure) {
        return new BasicAuthenticationHandler(LOGGER, CREDENTIALS, allowInsecure, now::get);
    }

    private static HttpRequest secureRequest(String username, String password) {
        return request("/mockserver/retrieve")
            .withSecure(true)
            .withRemoteAddress("10.1.2.3:40000")
            .withHeader(AUTHORIZATION.toString(), basic(username, password));
    }

    static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void shouldAuthenticateCorrectCredentialsAsTheUserWithItsScopes() {
        AuthenticationResult result = handler(false).authenticate(secureRequest("admin", "correct horse"));

        assertThat(result.isAuthenticated(), equalTo(true));
        assertThat(result.getPrincipal(), equalTo("admin"));
        assertThat(result.getPrincipalSource(), equalTo(BasicAuthenticationHandler.PRINCIPAL_SOURCE));
        assertThat(result.getScopes(), containsInAnyOrder("platform-admins", "qa-team"));
        assertThat(result.getClaims().get(BasicAuthenticationHandler.USERNAME_CLAIM), equalTo("admin"));
    }

    @Test
    public void shouldAuthenticateARepeatedRequestFromTheRememberedCheck() {
        BasicAuthenticationHandler handler = handler(false);

        assertThat(handler.authenticate(secureRequest("viewer", "viewer-pass")).getPrincipal(), equalTo("viewer"));
        assertThat(handler.authenticate(secureRequest("viewer", "viewer-pass")).getPrincipal(), equalTo("viewer"));
        assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("viewer", "viewer-pas")));
    }

    @Test
    public void shouldRefuseAWrongPasswordAndAnUnknownUserWithTheSameMessage() {
        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class, () -> handler(false).authenticate(secureRequest("admin", "wrong")));
        AuthenticationException unknownUser = assertThrows(AuthenticationException.class, () -> handler(false).authenticate(secureRequest("mallory", "correct horse")));

        assertThat(wrongPassword.getMessage(), equalTo("control plane request failed basic authentication"));
        assertThat(unknownUser.getMessage(), equalTo(wrongPassword.getMessage()));
    }

    @Test
    public void shouldNotLetOneUsersPasswordAuthenticateAnother() {
        assertThrows(AuthenticationException.class, () -> handler(false).authenticate(secureRequest("viewer", "correct horse")));
    }

    @Test
    public void shouldRefuseBasicCredentialsWithoutTls() {
        HttpRequest plainText = secureRequest("admin", "correct horse").withSecure(false);

        AuthenticationException refused = assertThrows(AuthenticationException.class, () -> handler(false).authenticate(plainText));

        assertThat(refused.getMessage(), equalTo("basic authentication requires TLS"));
        assertThrows(AuthenticationException.class, () -> handler(false).authenticate(secureRequest("admin", "correct horse").withSecure(null)));
    }

    @Test
    public void shouldAcceptBasicCredentialsWithoutTlsOnlyWhenInsecureIsAllowed() {
        HttpRequest plainText = secureRequest("admin", "correct horse").withSecure(false);

        assertThat(handler(true).authenticate(plainText).getPrincipal(), equalTo("admin"));
    }

    @Test
    public void shouldRefuseARequestWithoutBasicCredentials() {
        assertThat(assertThrows(AuthenticationException.class, () -> handler(false).authenticate(request().withSecure(true))).getMessage(),
            equalTo("no basic authorization header found"));
        assertThat(assertThrows(AuthenticationException.class, () -> handler(false).authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), "Bearer abc"))).getMessage(),
            equalTo("no basic authorization header found"));
        assertThat(assertThrows(AuthenticationException.class, () -> handler(false).authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), "Basic !!!"))).getMessage(),
            equalTo("basic authorization header is invalid format"));
        assertThat(assertThrows(AuthenticationException.class, () -> handler(false).authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), "Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8))))).getMessage(),
            equalTo("basic authorization header is invalid format"));
    }

    @Test
    public void shouldAcceptTheSchemeInAnyCase() {
        HttpRequest lowerCase = request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "correct horse").replace("Basic", "basic"));

        assertThat(handler(false).authenticate(lowerCase).getPrincipal(), equalTo("admin"));
    }

    @Test
    public void shouldBackOffASourceAfterRepeatedFailuresEvenForTheRightPassword() {
        BasicAuthenticationHandler handler = handler(false);
        for (int i = 0; i < BasicAuthenticationThrottle.FREE_FAILURES; i++) {
            assertThat(assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "guess"))).getMessage(),
                equalTo("control plane request failed basic authentication"));
        }

        AuthenticationException blocked = assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "correct horse")));
        assertThat(blocked.getMessage(), containsString("too many failed basic authentication attempts"));

        assertThat("another source is not blocked",
            handler.authenticate(secureRequest("viewer", "viewer-pass").withRemoteAddress("10.9.9.9:1")).getPrincipal(), equalTo("viewer"));

        now.addAndGet(BasicAuthenticationThrottle.BASE_BACKOFF_MILLIS);
        assertThat(handler.authenticate(secureRequest("admin", "correct horse").withRemoteAddress("10.1.2.3:50000")).getPrincipal(), equalTo("admin"));
        assertThat("a freshly verified success forgets the source", handler.trackedSources(), equalTo(0));
    }

    @Test
    public void shouldDoubleTheBackOffUpToItsLimit() {
        BasicAuthenticationThrottle throttle = new BasicAuthenticationThrottle(now::get);
        for (int i = 0; i < BasicAuthenticationThrottle.FREE_FAILURES; i++) {
            throttle.failed("source");
        }
        assertThat(throttle.retryAfterMillis("source"), equalTo(1_000L));
        throttle.failed("source");
        assertThat(throttle.retryAfterMillis("source"), equalTo(2_000L));
        for (int i = 0; i < 40; i++) {
            throttle.failed("source");
        }
        assertThat(throttle.retryAfterMillis("source"), equalTo(BasicAuthenticationThrottle.MAX_BACKOFF_MILLIS));

        now.addAndGet(BasicAuthenticationThrottle.FORGET_AFTER_MILLIS + 1);
        assertThat(throttle.retryAfterMillis("source"), equalTo(0L));
        assertThat(throttle.trackedSources(), equalTo(0));
    }

    @Test
    public void shouldBoundTheSourcesItTracks() {
        BasicAuthenticationThrottle throttle = new BasicAuthenticationThrottle(now::get);
        for (int i = 0; i < BasicAuthenticationThrottle.MAX_TRACKED_SOURCES + 500; i++) {
            throttle.failed("10.0." + (i / 256) + "." + (i % 256));
        }

        assertThat(throttle.trackedSources(), lessThanOrEqualTo(BasicAuthenticationThrottle.MAX_TRACKED_SOURCES));
    }

    @Test
    public void shouldThrottleBySourceHostNotByPort() {
        assertThat(BasicAuthenticationHandler.sourceOf(request().withRemoteAddress("10.1.2.3:40000")), equalTo("10.1.2.3"));
        assertThat(BasicAuthenticationHandler.sourceOf(request().withRemoteAddress("0:0:0:0:0:0:0:1:40000")), equalTo("0:0:0:0:0:0:0:1"));
        assertThat(BasicAuthenticationHandler.sourceOf(request().withRemoteAddress("[::1]:40000")), equalTo("::1"));
        assertThat(BasicAuthenticationHandler.sourceOf(request()), equalTo("unknown"));
    }

    @Test
    public void shouldNameTheCallerByUserName() {
        BasicAuthenticationHandler handler = handler(false);
        HttpRequest request = secureRequest("viewer", "viewer-pass");

        assertThat(handler.principalIdentity(request, handler.authenticate(request)), equalTo("basic:viewer"));
        assertThat(handler.principalIdentity(request, null), equalTo("basic:viewer"));
        assertThat(handler.principalIdentity(secureRequest("viewer", "nope"), null), equalTo(null));
    }

    @Test
    public void shouldLogFailuresAtMostOnceAnIntervalWithoutThePassword() {
        java.util.List<org.mockserver.log.model.LogEntry> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        MockServerLogger capturing = new MockServerLogger(BasicAuthenticationHandlerTest.class) {
            @Override
            public boolean isEnabledForInstance(org.slf4j.event.Level level) {
                return true;
            }

            @Override
            public void logEvent(org.mockserver.log.model.LogEntry logEntry) {
                warnings.add(logEntry);
            }
        };
        BasicAuthenticationHandler handler = new BasicAuthenticationHandler(capturing, CREDENTIALS, false, now::get);

        for (int i = 0; i < 3; i++) {
            assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "Guess-" + now.get())));
        }
        assertThat(warnings.size(), equalTo(1));

        now.addAndGet(BasicAuthenticationHandler.FAILURE_LOG_INTERVAL_SECONDS * 1_000L);
        assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "Guess-later")));

        assertThat(warnings.size(), equalTo(2));
        assertThat(warnings.get(1).getMessageFormat(), containsString("failed for user \"{}\" from {} ({} earlier failures not logged)"));
        assertThat(java.util.Arrays.asList(warnings.get(1).getArguments()), org.hamcrest.Matchers.contains("admin", "10.1.2.3", 2));
        for (org.mockserver.log.model.LogEntry warning : warnings) {
            assertThat(warning.getMessage(), not(containsString("Guess")));
        }
    }

    @Test
    public void shouldAcceptAPasswordContainingColonsAndRefuseAnEmptyUsername() {
        BasicAuthenticationCredentials credentials = BasicAuthenticationCredentials.parse("colon:" + PasswordHash.pbkdf2Sha256("a:b:c", 10_000), null);
        BasicAuthenticationHandler handler = new BasicAuthenticationHandler(LOGGER, credentials, false, now::get);

        assertThat(handler.authenticate(secureRequest("colon", "a:b:c")).getPrincipal(), equalTo("colon"));
        assertThat(assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("", "a:b:c"))).getMessage(),
            equalTo("basic authorization header is invalid format"));
    }

    @Test
    public void shouldThrottleRequestsWhoseTransportRecordsNoPeerInOneSharedRecord() {
        BasicAuthenticationHandler handler = handler(false);
        assertThat(handler.authenticate(secureRequest("viewer", "viewer-pass").withRemoteAddress(null)).getPrincipal(), equalTo("viewer"));
        for (int i = 0; i < BasicAuthenticationThrottle.FREE_FAILURES; i++) {
            assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "guess").withRemoteAddress(null)));
        }

        assertThat("never unthrottled", assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "correct horse").withRemoteAddress(null))).getMessage(),
            containsString("too many failed basic authentication attempts"));
        assertThat("an already verified credential still gets through", handler.authenticate(secureRequest("viewer", "viewer-pass").withRemoteAddress(null)).getPrincipal(), equalTo("viewer"));
        assertThat("a request with a peer is unaffected", handler.authenticate(secureRequest("admin", "correct horse")).getPrincipal(), equalTo("admin"));
    }

    @Test
    public void shouldAdmitAnAlreadyVerifiedCredentialFromABlockedSource() {
        BasicAuthenticationHandler handler = handler(false);
        assertThat(handler.authenticate(secureRequest("admin", "correct horse")).getPrincipal(), equalTo("admin"));
        for (int i = 0; i < BasicAuthenticationThrottle.FREE_FAILURES; i++) {
            assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "guess")));
        }

        assertThat(assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("viewer", "viewer-pass"))).getMessage(),
            containsString("too many failed basic authentication attempts"));
        assertThat("a user behind the same address who already authenticated is not locked out",
            handler.authenticate(secureRequest("admin", "correct horse")).getPrincipal(), equalTo("admin"));
        assertThat("and admitting them does not hand the guesser fresh guesses",
            assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("admin", "guess"))).getMessage(),
            containsString("too many failed basic authentication attempts"));
    }

    @Test
    public void shouldCheckOneHashOfEachConfiguredCostWhateverUserIsNamed() {
        CountingHash cheap = new CountingHash("cheap");
        CountingHash expensive = new CountingHash("expensive");
        CountingHash alsoCheap = new CountingHash("cheap");
        BasicAuthenticationHandler handler = new BasicAuthenticationHandler(LOGGER, BasicAuthenticationCredentials.of(
            new BasicAuthenticationCredentials.User("ci", cheap, java.util.Set.of()),
            new BasicAuthenticationCredentials.User("admin", expensive, java.util.Set.of()),
            new BasicAuthenticationCredentials.User("ops", alsoCheap, java.util.Set.of())
        ), false, now::get);

        assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("nobody", "x").withRemoteAddress("10.0.0.1:1")));
        assertThat(cheap.checks + alsoCheap.checks, equalTo(1));
        assertThat(expensive.checks, equalTo(1));

        assertThrows(AuthenticationException.class, () -> handler.authenticate(secureRequest("ops", "x").withRemoteAddress("10.0.0.2:1")));
        assertThat("the named user's own hash is checked for its cost", alsoCheap.checks, equalTo(1));
        assertThat(expensive.checks, equalTo(2));
    }

    private static final class CountingHash extends PasswordHash {
        private final String cost;
        private int checks;

        CountingHash(String cost) {
            this.cost = cost;
        }

        @Override
        public boolean matches(String password) {
            checks++;
            return false;
        }

        @Override
        public String algorithm() {
            return "counting";
        }

        @Override
        public String costKey() {
            return cost;
        }

        @Override
        public String weakness() {
            return null;
        }
    }

    @Test
    public void shouldNotEchoThePasswordInAnyFailure() {
        AuthenticationException failure = assertThrows(AuthenticationException.class, () -> handler(false).authenticate(secureRequest("admin", "SuperSecretGuess")));

        assertThat(failure.getMessage(), not(containsString("SuperSecretGuess")));
    }
}
