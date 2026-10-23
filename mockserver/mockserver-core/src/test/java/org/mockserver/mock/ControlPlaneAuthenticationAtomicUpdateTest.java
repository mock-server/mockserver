package org.mockserver.mock;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.configuration.AtomicConfigurationUpdate;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ControlPlaneAuthenticationSettings;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * A multi-field configuration update that switches control-plane authentication from one mechanism to
 * another must never let a request through unauthenticated while it is being applied.
 *
 * <p>{@code PUT /mockserver/configuration} can no longer change these settings (they are fixed at startup),
 * but an in-process {@link AtomicConfigurationUpdate} still can. Writing
 * {@code controlPlaneTLSMutualAuthenticationRequired} before {@code controlPlaneJWTAuthenticationRequired}
 * leaves a gap in which a gate reading the fields one by one saw "nothing required". The tests pause, or
 * widen, exactly that gap and check the gate from other threads.
 */
public class ControlPlaneAuthenticationAtomicUpdateTest {

    private static String jwkSource;

    private final List<Scheduler> schedulers = new ArrayList<>();
    private final List<HttpState> httpStates = new ArrayList<>();

    @BeforeClass
    public static void createJwkSource() throws Exception {
        // a loadable key set, so the JWT and OIDC handlers build and reject on the missing token
        File jwks = File.createTempFile("control-plane-jwks", ".json");
        jwks.deleteOnExit();
        Files.write(jwks.toPath(), new JWKSet(new RSAKeyGenerator(2048).keyID("test").generate().toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8));
        jwkSource = jwks.getAbsolutePath();
    }

    private static GatedConfiguration mtlsRequired() {
        GatedConfiguration configuration = new GatedConfiguration();
        configuration
            .controlPlaneTLSMutualAuthenticationRequired(true)
            // a chain, so the mTLS handler, not the refusal of an unusable mTLS set-up, rejects the request
            .controlPlaneTLSMutualAuthenticationCAChain("org/mockserver/authentication/mtls/ca.pem")
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneJWTAuthenticationJWKSource(jwkSource)
            .controlPlaneOidcAuthenticationRequired(false)
            .controlPlaneOidcJwksUri(jwkSource)
            .controlPlaneOidcAudience("mockserver");
        return configuration;
    }

    @After
    public void shutdownSchedulers() {
        httpStates.forEach(HttpState::stop);
        schedulers.forEach(Scheduler::shutdown);
    }

    private HttpState httpState(Configuration configuration) {
        MockServerLogger mockServerLogger = new MockServerLogger();
        Scheduler scheduler = new Scheduler(configuration, mockServerLogger);
        schedulers.add(scheduler);
        HttpState httpState = new HttpState(configuration, mockServerLogger, scheduler);
        httpStates.add(httpState);
        return httpState;
    }

    private static boolean allowedWithoutCredentials(HttpState httpState) {
        return httpState.evaluateControlPlaneAuthentication(request().withMethod("PUT").withPath("/mockserver/retrieve")).isAllowed();
    }

    @Test
    public void shouldNotAllowAnUnauthenticatedRequestWhileSwitchingFromMtlsToJwt() throws Exception {
        assertNeverOpenMidSwitch("jwt", configuration -> configuration
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneJWTAuthenticationRequired(true));
    }

    @Test
    public void shouldNotAllowAnUnauthenticatedRequestWhileSwitchingFromMtlsToOidc() throws Exception {
        assertNeverOpenMidSwitch("oidc", configuration -> configuration
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(true));
    }

    private void assertNeverOpenMidSwitch(String pausePoint, Consumer<Configuration> switchToTokenAuthentication) throws Exception {
        GatedConfiguration configuration = mtlsRequired();
        HttpState httpState = httpState(configuration);
        assertThat("a request without a client certificate must be rejected before the switch", allowedWithoutCredentials(httpState), is(false));
        assertThat("the mTLS handler, not a deny-all fallback, must reject it", httpState.getControlPlaneAuthenticationHandler(), instanceOf(MTLSAuthenticationHandler.class));

        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        configuration.onTokenAuthenticationWrite(pausePoint, () -> {
            paused.countDown();
            await(resume);
        });
        Thread put = new Thread(() -> AtomicConfigurationUpdate.apply(configuration, () -> switchToTokenAuthentication.accept(configuration)), "configuration-put");
        put.start();
        assertThat("the update never reached the pause point", paused.await(10, TimeUnit.SECONDS), is(true));
        try {
            // the update has written mTLS=false and not yet the token mechanism's =true
            assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), is(false));
            AtomicBoolean allowed = new AtomicBoolean();
            Thread request = new Thread(() -> allowed.set(allowedWithoutCredentials(httpState)), "control-plane-request");
            request.start();
            request.join(TimeUnit.SECONDS.toMillis(10));
            assertThat("the gate must not block on an update in progress", request.isAlive(), is(false));
            assertThat("a request arriving mid-update must not pass without credentials", allowed.get(), is(false));
        } finally {
            resume.countDown();
            put.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat("the token mechanism is required once the update completes", allowedWithoutCredentials(httpState), is(false));
    }

    @Test
    public void shouldAuthorizeFromTheOldSettingsWhilePausedBetweenTheAuthorizationFlagAndTheScopeMapping() throws Exception {
        GatedConfiguration configuration = new GatedConfiguration();
        configuration.controlPlaneAuthorizationEnabled(false).controlPlaneScopeMapping(Collections.emptyMap());
        assertAuthorizedFromOldSettingsMidPut(configuration, "scopeMapping", update -> update
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(Collections.singletonMap("team", ControlPlaneRole.READ)));
    }

    @Test
    public void shouldAuthorizeFromTheOldScopeMappingWhilePausedAfterIt() throws Exception {
        GatedConfiguration configuration = new GatedConfiguration();
        configuration.controlPlaneAuthorizationEnabled(true).controlPlaneScopeMapping(Collections.singletonMap("team", ControlPlaneRole.MUTATE));
        assertAuthorizedFromOldSettingsMidPut(configuration, "afterScopeMapping", update -> update
            .controlPlaneScopeMapping(Collections.singletonMap("team", ControlPlaneRole.READ))
            .transparentProxyEnabled(false));
    }

    /**
     * The old settings allow a mutation by a principal with scope "team"; the new ones only grant it READ.
     */
    private void assertAuthorizedFromOldSettingsMidPut(GatedConfiguration configuration, String pausePoint, Consumer<Configuration> update) throws Exception {
        HttpState httpState = httpState(configuration);
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated("principal", "test", Collections.emptyMap(), Collections.singleton("team"));
            }
        });
        HttpRequest mutation = request().withMethod("PUT").withPath("/mockserver/clear");
        assertThat("the old settings allow the mutation", httpState.evaluateControlPlaneAuthentication(mutation).outcome(), is(HttpState.ControlPlaneAuthOutcome.ALLOWED));

        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        configuration.onTokenAuthenticationWrite(pausePoint, () -> {
            paused.countDown();
            await(resume);
        });
        Thread put = new Thread(() -> AtomicConfigurationUpdate.apply(configuration, () -> update.accept(configuration)), "configuration-put");
        put.start();
        assertThat("the update never reached the pause point", paused.await(10, TimeUnit.SECONDS), is(true));
        try {
            AtomicReference<HttpState.ControlPlaneAuthOutcome> outcome = new AtomicReference<>();
            Thread request = new Thread(() -> outcome.set(httpState.evaluateControlPlaneAuthentication(mutation).outcome()), "control-plane-request");
            request.start();
            request.join(TimeUnit.SECONDS.toMillis(10));
            assertThat("a request mid-update is authorized from the old settings, all of them", outcome.get(), is(HttpState.ControlPlaneAuthOutcome.ALLOWED));
        } finally {
            resume.countDown();
            put.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat("the new settings only grant READ", httpState.evaluateControlPlaneAuthentication(mutation).outcome(), is(HttpState.ControlPlaneAuthOutcome.FORBIDDEN));
    }

    @Test
    public void shouldAuthorizeAToolCallFromTheSnapshotItWasAuthenticatedUnder() {
        Configuration configuration = new Configuration();
        configuration.controlPlaneAuthorizationEnabled(true).controlPlaneScopeMapping(Collections.singletonMap("team", ControlPlaneRole.READ));
        HttpState httpState = httpState(configuration);
        ControlPlaneAuthenticationSettings authenticatedUnder = httpState.controlPlaneAuthenticationSettings();

        configuration.controlPlaneAuthorizationEnabled(false);

        assertThat("authorized from the snapshot the caller was authenticated under, which only grants READ",
            httpState.controlPlaneToolAuthorized(authenticatedUnder, Collections.singleton("team"), false, "create_expectation"), is(false));
        assertThat("a caller authenticated after the change is authorized from the new settings",
            httpState.controlPlaneToolAuthorized(httpState.controlPlaneAuthenticationSettings(), Collections.singleton("team"), false, "create_expectation"), is(true));
    }

    @Test
    public void shouldNeverAllowAnUnauthenticatedRequestWhilePutsFlipBetweenMechanisms() throws Exception {
        GatedConfiguration configuration = mtlsRequired();
        HttpState httpState = httpState(configuration);
        // widen the gap between the two writes so a racing request reliably lands in it
        configuration.onTokenAuthenticationWrite("jwt", () -> LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(200)));

        AtomicBoolean flipping = new AtomicBoolean(true);
        AtomicLong requests = new AtomicLong();
        AtomicLong allowed = new AtomicLong();
        List<Thread> requesters = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread requester = new Thread(() -> {
                while (flipping.get()) {
                    requests.incrementAndGet();
                    if (allowedWithoutCredentials(httpState)) {
                        allowed.incrementAndGet();
                    }
                }
            }, "control-plane-requester-" + i);
            requester.start();
            requesters.add(requester);
        }
        try {
            // keep flipping until the requesters have raced plenty of updates, however the threads are scheduled
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            for (int flip = 0; (flip < 200 || requests.get() < 2_000) && System.nanoTime() < deadline; flip++) {
                boolean toJwt = flip % 2 == 0;
                AtomicConfigurationUpdate.apply(configuration, () -> configuration
                    .controlPlaneTLSMutualAuthenticationRequired(!toJwt)
                    .controlPlaneJWTAuthenticationRequired(toJwt));
            }
        } finally {
            flipping.set(false);
            for (Thread requester : requesters) {
                requester.join(TimeUnit.SECONDS.toMillis(10));
            }
        }

        assertThat(requests.get(), greaterThanOrEqualTo(2_000L));
        assertThat("requests allowed without credentials while updates switched mechanism", allowed.get(), is(0L));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs a hook just before a chosen setter, so a test can pause or widen the window inside an update.
     */
    private static class GatedConfiguration extends Configuration {
        private volatile String hookOn;
        private volatile Runnable hook;

        void onTokenAuthenticationWrite(String mechanism, Runnable hook) {
            this.hook = hook;
            this.hookOn = mechanism;
        }

        private void runHook(String mechanism) {
            Runnable current = hook;
            if (current != null && mechanism.equals(hookOn)) {
                current.run();
            }
        }

        @Override
        public Configuration controlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
            runHook("jwt");
            return super.controlPlaneJWTAuthenticationRequired(controlPlaneJWTAuthenticationRequired);
        }

        @Override
        public Configuration controlPlaneOidcAuthenticationRequired(Boolean controlPlaneOidcAuthenticationRequired) {
            runHook("oidc");
            return super.controlPlaneOidcAuthenticationRequired(controlPlaneOidcAuthenticationRequired);
        }

        @Override
        public Configuration controlPlaneScopeMapping(Map<String, ControlPlaneRole> controlPlaneScopeMapping) {
            runHook("scopeMapping");
            return super.controlPlaneScopeMapping(controlPlaneScopeMapping);
        }

        @Override
        public Configuration transparentProxyEnabled(Boolean transparentProxyEnabled) {
            runHook("afterScopeMapping");
            return super.transparentProxyEnabled(transparentProxyEnabled);
        }
    }
}
