package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.configuration.AtomicConfigurationUpdate;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.model.ConfigurationDTO;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * Asserts that enabling control-plane authentication actually DENIES, through every configuration route.
 *
 * <p>Each control-plane authentication mechanism already had a positive test (it works when configured at
 * startup) and no negative test (it actually denies). That asymmetry hid a serious defect: the handler
 * chain was built ONCE during server bootstrap and pushed into {@link HttpState}, so enabling
 * authentication afterwards through any route was accepted — {@code PUT} returned 200, {@code GET} echoed
 * back {@code true} — while {@code HttpState.controlPlaneAuthenticationHandler} stayed {@code null}, and a
 * null handler maps to "authenticated". An operator hardening a running shared/CI instance was told it was
 * locked; it was fully open, including the recorded request log, which in proxy mode holds real captured
 * credentials.
 *
 * <p>This is parameterised over the configuration ROUTES rather than written once per control, because the
 * defect was never in a single control — it was in the assumption that any route reaches the enforcement
 * point. A per-route harness closes the category rather than one instance of it.
 *
 * <p>{@code PUT /mockserver/configuration} cannot change these settings (they are fixed at startup, see
 * {@code HttpStateConfigurationUpdateTest}); the in-process routes below are the ones that still can.
 */
@RunWith(Parameterized.class)
public class ControlPlaneAuthenticationRouteDenialTest {

    /**
     * Enables a control-plane authentication mechanism on the supplied Configuration via one route.
     */
    interface Route extends BiConsumer<Configuration, Mechanism> {
    }

    enum Mechanism {
        JWT, MTLS, OIDC
    }

    // a chain, so the mTLS rows exercise the mTLS handler rather than the refusal of an unusable mTLS set-up
    private static final String CONTROL_PLANE_CA_CHAIN = "org/mockserver/authentication/mtls/ca.pem";

    @Parameterized.Parameters(name = "{0} via {1}")
    public static Collection<Object[]> routes() {
        return Arrays.asList(new Object[][]{
            {Mechanism.JWT, "system property", (Route) (configuration, mechanism) ->
                ConfigurationProperties.controlPlaneJWTAuthenticationRequired(true)},
            {Mechanism.JWT, "Configuration instance", (Route) (configuration, mechanism) ->
                configuration.controlPlaneJWTAuthenticationRequired(true)},
            {Mechanism.JWT, "AtomicConfigurationUpdate", (Route) (configuration, mechanism) ->
                AtomicConfigurationUpdate.apply(configuration, () -> configuration.controlPlaneJWTAuthenticationRequired(true))},

            {Mechanism.MTLS, "system property", (Route) (configuration, mechanism) -> {
                ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN);
                ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(true);
            }},
            {Mechanism.MTLS, "Configuration instance", (Route) (configuration, mechanism) ->
                configuration.controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN).controlPlaneTLSMutualAuthenticationRequired(true)},
            {Mechanism.MTLS, "AtomicConfigurationUpdate", (Route) (configuration, mechanism) ->
                AtomicConfigurationUpdate.apply(configuration, () -> configuration.controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN).controlPlaneTLSMutualAuthenticationRequired(true))},

            {Mechanism.OIDC, "system property", (Route) (configuration, mechanism) ->
                ConfigurationProperties.controlPlaneOidcAuthenticationRequired(true)},
            {Mechanism.OIDC, "Configuration instance", (Route) (configuration, mechanism) ->
                configuration.controlPlaneOidcAuthenticationRequired(true)},
            {Mechanism.OIDC, "AtomicConfigurationUpdate", (Route) (configuration, mechanism) ->
                AtomicConfigurationUpdate.apply(configuration, () -> configuration.controlPlaneOidcAuthenticationRequired(true))},
        });
    }

    /**
     * Serialises every parameterisation's {@code setUp -> test body -> tearDown} against each other.
     *
     * <p>The system-property routes enable authentication through the process-global
     * {@link ConfigurationProperties} static store, which cannot be thread-isolated. The class is
     * therefore excluded from the parallel {@code default-test} phase and pinned to the sequential phase
     * in {@code mockserver-core/pom.xml}. That pom routing is correct but is silently bypassed by a
     * {@code -Dtest=ControlPlaneAuthenticationRouteDenialTest} filter, because {@code -Dtest} overrides
     * the surefire {@code <excludes>} and runs the class under {@code parallel=classes} — where JUnit4
     * {@link Parameterized} runs up to {@code threadCount} parameterisations CONCURRENTLY. Two such
     * parameterisations then race on the shared static store (one enables it, another's reset clears it),
     * producing intermittent false denials/allows. A filtered verification run is exactly what an agent
     * or a developer does, so the flake must not depend on the pom routing alone.
     *
     * <p>Holding this lock across each parameterisation's whole lifecycle makes the concurrent case behave
     * identically to the sequential case, deterministically. It is a no-op cost in the normal sequential
     * phase (uncontended).</p>
     */
    private static final ReentrantLock GLOBAL_STATIC_STATE_LOCK = new ReentrantLock();

    @Parameterized.Parameter(0)
    public Mechanism mechanism;

    @Parameterized.Parameter(1)
    public String routeName;

    @Parameterized.Parameter(2)
    public Route route;

    private Configuration configuration;
    private HttpState httpState;
    private Scheduler scheduler;

    @Before
    public void setUp() {
        // acquire FIRST, before touching any global static, so a concurrently-scheduled parameterisation
        // cannot interleave with this one's system-property mutations (see GLOBAL_STATIC_STATE_LOCK).
        GLOBAL_STATIC_STATE_LOCK.lock();
        clearControlPlaneSystemProperties();
        configuration = Configuration.configuration();
        MockServerLogger mockServerLogger = new MockServerLogger();
        scheduler = new Scheduler(configuration, mockServerLogger);
        httpState = new HttpState(configuration, mockServerLogger, scheduler);
    }

    @After
    public void tearDown() {
        try {
            if (httpState != null) {
                httpState.stop();
            }
            clearControlPlaneSystemProperties();
            if (scheduler != null) {
                scheduler.shutdown();
            }
        } finally {
            // release LAST, only if this thread holds it (defensive: @After runs even if setUp threw before
            // the lock was taken).
            if (GLOBAL_STATIC_STATE_LOCK.isHeldByCurrentThread()) {
                GLOBAL_STATIC_STATE_LOCK.unlock();
            }
        }
    }

    /**
     * Reset the STATIC configuration store between parameterisations. Note that
     * {@code System.clearProperty} alone is not enough: {@code ConfigurationProperties.setProperty} also
     * writes into its own static {@code PROPERTIES} store, which is read in preference to a cleared system
     * property — so the enabled state would leak into the next test and the baseline assertion would fail.
     * This class therefore mutates global static state and MUST run in the sequential test phase.
     */
    private static void clearControlPlaneSystemProperties() {
        ConfigurationProperties.controlPlaneJWTAuthenticationRequired(false);
        ConfigurationProperties.controlPlaneTLSMutualAuthenticationRequired(false);
        ConfigurationProperties.controlPlaneOidcAuthenticationRequired(false);
        ConfigurationProperties.controlPlaneTLSMutualAuthenticationCAChain("");
        System.clearProperty("mockserver.controlPlaneJWTAuthenticationRequired");
        System.clearProperty("mockserver.controlPlaneTLSMutualAuthenticationCAChain");
        System.clearProperty("mockserver.controlPlaneTLSMutualAuthenticationRequired");
        System.clearProperty("mockserver.controlPlaneOidcAuthenticationRequired");
    }

    @Test
    public void shouldAllowBeforeControlPlaneAuthenticationIsEnabled() {
        // baseline: the control plane is open by default, so the assertions below are meaningful
        assertThat(httpState.evaluateControlPlaneAuthentication(request().withPath("/mockserver/retrieve")).isAllowed(),
            is(true));
    }

    @Test
    public void shouldDenyOnceControlPlaneAuthenticationIsEnabled() {
        route.accept(configuration, mechanism);

        // an unauthenticated control-plane request must NOT be allowed. Before the fix this returned
        // ALLOWED for every route: the handler was fixed at bootstrap, so the configuration change was
        // accepted and reported as applied while the enforcement point still saw a null handler.
        assertThat("control plane must DENY unauthenticated requests once " + mechanism
                + " authentication is enabled via " + routeName,
            httpState.evaluateControlPlaneAuthentication(request().withPath("/mockserver/retrieve")).isAllowed(),
            is(false));
        if (mechanism == Mechanism.MTLS) {
            assertThat("the mTLS handler, not a deny-all fallback, must make the decision",
                httpState.getControlPlaneAuthenticationHandler(), instanceOf(MTLSAuthenticationHandler.class));
        }
    }

    @Test
    public void shouldStillDenyAfterAFurtherRuntimeReconfiguration() {
        route.accept(configuration, mechanism);
        // force the handler to be resolved (as a first control-plane request would)
        httpState.evaluateControlPlaneAuthentication(request());

        // a subsequent unrelated runtime reconfiguration must not re-open the control plane
        new ConfigurationDTO().setMaxExpectations(1234).applyTo(configuration);

        assertThat("control plane must still DENY after an unrelated runtime reconfiguration",
            httpState.evaluateControlPlaneAuthentication(request().withPath("/mockserver/retrieve")).isAllowed(),
            is(false));
    }

    @Test
    public void shouldReopenWhenControlPlaneAuthenticationIsDisabledAgain() {
        route.accept(configuration, mechanism);
        assertThat(httpState.evaluateControlPlaneAuthentication(request()).isAllowed(), is(false));

        // disabling must ALSO take effect — the derived handler is keyed on the configuration, so turning
        // the mechanism off releases it rather than leaving a stale handler denying forever
        clearControlPlaneSystemProperties();
        configuration
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false);

        assertThat(httpState.evaluateControlPlaneAuthentication(request()).isAllowed(), is(true));
    }

    @Test
    public void explicitlyInstalledHandlerStillWins() {
        // embedded users and existing tests install their own handler; the configuration-derived handler
        // must never override it
        httpState.setControlPlaneAuthenticationHandler(request -> true);
        route.accept(configuration, mechanism);

        assertThat(httpState.evaluateControlPlaneAuthentication(request()).isAllowed(), is(true));
    }
}
