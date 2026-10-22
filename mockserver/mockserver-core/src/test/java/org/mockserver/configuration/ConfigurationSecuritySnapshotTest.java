package org.mockserver.configuration;

import org.junit.Test;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.serialization.model.ConfigurationDTO;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * The control-plane authentication and server TLS snapshots must agree with the {@link Configuration}
 * getters, and a multi-field update must reach them all at once.
 */
public class ConfigurationSecuritySnapshotTest {

    private static final String EXISTING_PEM = ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;

    // ---- every snapshot accessor agrees with the getter of the same name, unset and set ----

    @Test
    public void controlPlaneAuthenticationSnapshotShouldMatchTheGettersForEveryValue() throws Exception {
        List<String> checked = assertSnapshotMatchesGetters(ControlPlaneAuthenticationSettings.class,
            configuration -> ControlPlaneAuthenticationSettings.of(configuration));
        assertThat(checked, hasSize(19));
        assertThat(checked, hasItems("controlPlaneTLSMutualAuthenticationRequired", "controlPlaneJWTAuthenticationRequired",
            "controlPlaneOidcAuthenticationRequired", "controlPlaneAuthorizationEnabled", "controlPlaneScopeMapping",
            "controlPlaneBasicAuthenticationRequired", "controlPlaneBasicAuthenticationCredentials"));
    }

    @Test
    public void serverTlsSnapshotShouldMatchTheGettersForEveryValue() throws Exception {
        List<String> checked = assertSnapshotMatchesGetters(ServerTlsSettings.class, ServerTlsSettings::of);
        assertThat(checked, hasSize(12));
        assertThat(checked, hasItems("tlsMutualAuthenticationRequired", "tlsMutualAuthenticationCertificateChain", "certificateAuthorityCertificate"));
    }

    @Test
    public void serverTlsSnapshotShouldApplyProxySetupToDynamicCertificateAuthority() {
        Configuration configuration = new Configuration().dynamicallyCreateCertificateAuthorityCertificate(false);
        assertThat(ServerTlsSettings.of(configuration).dynamicallyCreateCertificateAuthorityCertificate(), is(false));

        configuration.proxySetup(true);

        assertThat(ServerTlsSettings.of(configuration).dynamicallyCreateCertificateAuthorityCertificate(), is(true));
        assertThat(configuration.dynamicallyCreateCertificateAuthorityCertificate(), is(true));
    }

    private interface SnapshotReader {
        Object read(Configuration configuration);
    }

    private static List<String> assertSnapshotMatchesGetters(Class<?> snapshotType, SnapshotReader reader) throws Exception {
        List<String> checked = new ArrayList<>();
        for (Method accessor : snapshotType.getDeclaredMethods()) {
            if (!Modifier.isPublic(accessor.getModifiers()) || Modifier.isStatic(accessor.getModifiers()) || accessor.getParameterCount() != 0) {
                continue;
            }
            Method getter;
            try {
                getter = Configuration.class.getMethod(accessor.getName());
            } catch (NoSuchMethodException notAGetter) {
                continue;
            }
            Method setter = Configuration.class.getMethod(accessor.getName(), getter.getReturnType());

            Configuration unset = new Configuration();
            assertThat(accessor.getName() + " unset", accessor.invoke(reader.read(unset)), is(getter.invoke(unset)));

            Configuration set = new Configuration();
            Object distinctive = distinctiveValue(accessor.getName(), getter.getReturnType(), getter.invoke(set));
            setter.invoke(set, distinctive);
            assertThat(accessor.getName() + " set", accessor.invoke(reader.read(set)), is(distinctive));
            assertThat(accessor.getName() + " set", accessor.invoke(reader.read(set)), is(getter.invoke(set)));
            checked.add(accessor.getName());
        }
        return checked;
    }

    private static Object distinctiveValue(String name, Class<?> type, Object current) {
        if (type == Boolean.class) {
            return !Boolean.TRUE.equals(current);
        }
        if (type == String.class) {
            // the chain and users file setters check the file exists
            return name.endsWith("Chain") || name.endsWith("File") ? EXISTING_PEM : "distinctive-" + name;
        }
        if (type == Set.class) {
            return Collections.singleton("distinctive-" + name);
        }
        if (type == Map.class) {
            return name.equals("controlPlaneScopeMapping")
                ? Collections.singletonMap("distinctive", ControlPlaneRole.MUTATE)
                : Collections.singletonMap("distinctive", name);
        }
        throw new AssertionError("no distinctive value for " + name + " of " + type);
    }

    // ---- a multi-field update reaches the snapshots all at once ----

    @Test
    public void shouldShowEveryOldControlPlaneValueUntilAnUpdateCompletes() throws Exception {
        PausingConfiguration configuration = new PausingConfiguration();
        configuration.controlPlaneTLSMutualAuthenticationRequired(true).controlPlaneJWTAuthenticationRequired(false);
        configuration.pauseOnNextJwtWrite();

        Thread put = configuration.applyInBackground(new ConfigurationDTO()
            .setControlPlaneTLSMutualAuthenticationRequired(false)
            .setControlPlaneJWTAuthenticationRequired(true));
        configuration.awaitPaused();
        try {
            // mid-update: the mTLS field is already false, the JWT field not yet true
            assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), is(false));
            assertThat(configuration.controlPlaneJWTAuthenticationRequired(), is(false));
            ControlPlaneAuthenticationSettings midUpdate = readOnAnotherThread(() -> ControlPlaneAuthenticationSettings.of(configuration));
            assertThat(midUpdate.controlPlaneTLSMutualAuthenticationRequired(), is(true));
            assertThat(midUpdate.controlPlaneJWTAuthenticationRequired(), is(false));
            assertThat(midUpdate.authenticationRequired(), is(true));
        } finally {
            configuration.resume();
            put.join(TimeUnit.SECONDS.toMillis(10));
        }

        ControlPlaneAuthenticationSettings afterUpdate = ControlPlaneAuthenticationSettings.of(configuration);
        assertThat(afterUpdate.controlPlaneTLSMutualAuthenticationRequired(), is(false));
        assertThat(afterUpdate.controlPlaneJWTAuthenticationRequired(), is(true));
    }

    @Test
    public void shouldShowEveryOldServerTlsValueUntilAnUpdateCompletes() throws Exception {
        PausingConfiguration configuration = new PausingConfiguration();
        configuration.tlsMutualAuthenticationRequired(false).http2Enabled(true);
        configuration.pauseOnNextHttp2Write();

        // applyTo writes tlsMutualAuthenticationRequired and its chain before http2Enabled
        Thread put = configuration.applyInBackground(new ConfigurationDTO()
            .setTlsMutualAuthenticationRequired(true)
            .setTlsMutualAuthenticationCertificateChain(EXISTING_PEM)
            .setHttp2Enabled(false));
        configuration.awaitPaused();
        long generationMidUpdate;
        try {
            assertThat(configuration.tlsMutualAuthenticationRequired(), is(true));
            ServerTlsSettings midUpdate = readOnAnotherThread(() -> ServerTlsSettings.of(configuration));
            assertThat(midUpdate.tlsMutualAuthenticationRequired(), is(false));
            assertThat(midUpdate.tlsMutualAuthenticationCertificateChain(), is(not(EXISTING_PEM)));
            assertThat(midUpdate.http2Enabled(), is(true));
            generationMidUpdate = configuration.serverTLSContextGeneration();
        } finally {
            configuration.resume();
            put.join(TimeUnit.SECONDS.toMillis(10));
        }

        ServerTlsSettings afterUpdate = ServerTlsSettings.of(configuration);
        assertThat(afterUpdate.tlsMutualAuthenticationRequired(), is(true));
        assertThat(afterUpdate.tlsMutualAuthenticationCertificateChain(), is(EXISTING_PEM));
        assertThat(afterUpdate.http2Enabled(), is(false));
        // publishing the snapshot must move the generation, or a cached context validated mid-update is kept
        assertThat(configuration.serverTLSContextGeneration(), greaterThan(generationMidUpdate));
    }

    @Test
    public void shouldPublishSetterChangesImmediatelyOutsideAnUpdate() {
        Configuration configuration = new Configuration();
        ControlPlaneAuthenticationSettings before = ControlPlaneAuthenticationSettings.of(configuration);

        configuration.controlPlaneOidcAuthenticationRequired(true);

        assertThat(ControlPlaneAuthenticationSettings.of(configuration), is(not(sameInstance(before))));
        assertThat(ControlPlaneAuthenticationSettings.of(configuration).controlPlaneOidcAuthenticationRequired(), is(true));
    }

    @Test
    public void shouldDeferPublicationUntilTheOutermostUpdateCompletes() {
        Configuration configuration = new Configuration();
        AtomicReference<Boolean> seenInsideNestedUpdate = new AtomicReference<>();

        AtomicConfigurationUpdate.apply(configuration, () -> {
            AtomicConfigurationUpdate.apply(configuration, () -> configuration.controlPlaneJWTAuthenticationRequired(true));
            seenInsideNestedUpdate.set(ControlPlaneAuthenticationSettings.of(configuration).controlPlaneJWTAuthenticationRequired());
        });

        assertThat(seenInsideNestedUpdate.get(), is(false));
        assertThat(ControlPlaneAuthenticationSettings.of(configuration).controlPlaneJWTAuthenticationRequired(), is(true));
    }

    @Test
    public void shouldRestoreAuthenticationAndTlsValuesWhenAnUpdateThrows() {
        Configuration configuration = new Configuration()
            .controlPlaneTLSMutualAuthenticationRequired(true)
            .controlPlaneOidcAuthenticationRequired(false)
            .tlsMutualAuthenticationRequired(false);

        try {
            AtomicConfigurationUpdate.apply(configuration, () -> {
                configuration.controlPlaneTLSMutualAuthenticationRequired(false);
                configuration.controlPlaneOidcAuthenticationRequired(true);
                configuration.tlsMutualAuthenticationRequired(true);
                throw new IllegalArgumentException("rejected mid-update");
            });
        } catch (IllegalArgumentException expected) {
            // a rejected update must change nothing in the authentication and TLS groups
        }

        assertThat(configuration.controlPlaneTLSMutualAuthenticationRequired(), is(true));
        assertThat(configuration.controlPlaneOidcAuthenticationRequired(), is(false));
        assertThat(configuration.tlsMutualAuthenticationRequired(), is(false));
        ControlPlaneAuthenticationSettings settings = ControlPlaneAuthenticationSettings.of(configuration);
        assertThat(settings.authenticationRequired(), is(true));
        assertThat(settings.controlPlaneTLSMutualAuthenticationRequired(), is(true));
        assertThat(settings.controlPlaneOidcAuthenticationRequired(), is(false));
        assertThat(ServerTlsSettings.of(configuration).tlsMutualAuthenticationRequired(), is(false));
    }

    @Test
    public void shouldRejectAnInvalidPutBeforeWritingAnything() {
        String missing = "/no/such/directory/missing.pem";
        Map<String, java.util.function.UnaryOperator<ConfigurationDTO>> invalidValues = new java.util.LinkedHashMap<>();
        invalidValues.put("controlPlaneTLSMutualAuthenticationCAChain", dto -> dto.setControlPlaneTLSMutualAuthenticationCAChain(missing));
        invalidValues.put("controlPlanePrivateKeyPath", dto -> dto.setControlPlanePrivateKeyPath(missing));
        invalidValues.put("controlPlaneX509CertificatePath", dto -> dto.setControlPlaneX509CertificatePath(missing));
        invalidValues.put("tlsMutualAuthenticationCertificateChain", dto -> dto.setTlsMutualAuthenticationCertificateChain(missing));
        invalidValues.put("forwardProxyTLSCustomTrustX509Certificates", dto -> dto.setForwardProxyTLSCustomTrustX509Certificates(missing));
        invalidValues.put("forwardProxyPrivateKey", dto -> dto.setForwardProxyPrivateKey(missing));
        invalidValues.put("forwardProxyCertificateChain", dto -> dto.setForwardProxyCertificateChain(missing));
        invalidValues.put("globalResponseDelayMillis", dto -> dto.setGlobalResponseDelayMillis(-1L));

        assertEachIsRejectedBeforeAnythingIsWritten(invalidValues);
    }

    @Test
    public void shouldRejectAMaskedCertificatePathBeforeWritingAnything() {
        // only the two private-key paths have a held value to keep in place of the mask; for these five
        // the mask is just a file that does not exist, so it must be refused before any write
        String masked = ConfigurationProperties.REDACTED_VALUE;
        Map<String, java.util.function.UnaryOperator<ConfigurationDTO>> maskedValues = new java.util.LinkedHashMap<>();
        maskedValues.put("controlPlaneTLSMutualAuthenticationCAChain", dto -> dto.setControlPlaneTLSMutualAuthenticationCAChain(masked));
        maskedValues.put("controlPlaneX509CertificatePath", dto -> dto.setControlPlaneX509CertificatePath(masked));
        maskedValues.put("tlsMutualAuthenticationCertificateChain", dto -> dto.setTlsMutualAuthenticationCertificateChain(masked));
        maskedValues.put("forwardProxyTLSCustomTrustX509Certificates", dto -> dto.setForwardProxyTLSCustomTrustX509Certificates(masked));
        maskedValues.put("forwardProxyCertificateChain", dto -> dto.setForwardProxyCertificateChain(masked));

        assertEachIsRejectedBeforeAnythingIsWritten(maskedValues);
    }

    private static void assertEachIsRejectedBeforeAnythingIsWritten(Map<String, java.util.function.UnaryOperator<ConfigurationDTO>> invalidValues) {
        for (Map.Entry<String, java.util.function.UnaryOperator<ConfigurationDTO>> invalid : invalidValues.entrySet()) {
            Configuration configuration = new Configuration()
                .controlPlaneTLSMutualAuthenticationRequired(true)
                .controlPlaneOidcAuthenticationRequired(false)
                .maxExpectations(100);
            ConfigurationDTO put = invalid.getValue().apply(new ConfigurationDTO()
                .setControlPlaneTLSMutualAuthenticationRequired(false)
                .setControlPlaneOidcAuthenticationRequired(true)
                .setMaxExpectations(4321));

            RuntimeException rejected = null;
            try {
                put.applyTo(configuration);
            } catch (RuntimeException exception) {
                rejected = exception;
            }

            // maxExpectations is written first in applyTo, so it shows nothing was written at all
            assertThat(invalid.getKey(), configuration.maxExpectations(), is(100));
            assertThat(invalid.getKey(), configuration.controlPlaneTLSMutualAuthenticationRequired(), is(true));
            assertThat(invalid.getKey(), ControlPlaneAuthenticationSettings.of(configuration).authenticationRequired(), is(true));
            assertThat("a PUT with an invalid " + invalid.getKey() + " must be rejected", rejected, instanceOf(IllegalArgumentException.class));
            assertThat(rejected.getMessage(), containsString(invalid.getKey()));
        }
    }

    @Test
    public void shouldCountMultiFieldUpdatesButNotSingleSetters() {
        Configuration configuration = new Configuration();
        ServerTlsSettings initial = ServerTlsSettings.of(configuration);

        configuration.privateKeyPath("derived/PKCS8PrivateKey.pem");
        assertThat(ServerTlsSettings.of(configuration).updatedSince(initial), is(false));

        new ConfigurationDTO().setTlsProtocols("TLSv1.3").applyTo(configuration);
        assertThat(ServerTlsSettings.of(configuration).updatedSince(initial), is(true));
    }

    private static <T> T readOnAnotherThread(java.util.concurrent.Callable<T> read) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                result.set(read.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        reader.start();
        reader.join(TimeUnit.SECONDS.toMillis(10));
        assertThat("the reader must not block on an update in progress", reader.isAlive(), is(false));
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /**
     * Pauses {@link ConfigurationDTO#applyTo} at a chosen setter, after the fields written before it.
     */
    static class PausingConfiguration extends Configuration {
        private volatile String pauseOn;
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        void pauseOnNextJwtWrite() {
            pauseOn = "jwt";
        }

        void pauseOnNextHttp2Write() {
            pauseOn = "http2";
        }

        void awaitPaused() throws InterruptedException {
            assertThat("the update never reached the pause point", paused.await(10, TimeUnit.SECONDS), is(true));
        }

        void resume() {
            resume.countDown();
        }

        Thread applyInBackground(ConfigurationDTO update) {
            Thread thread = new Thread(() -> update.applyTo(this), "paused-configuration-update");
            thread.start();
            return thread;
        }

        private void pauseIf(String point) {
            if (point.equals(pauseOn)) {
                pauseOn = null;
                paused.countDown();
                try {
                    resume.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public Configuration controlPlaneJWTAuthenticationRequired(Boolean controlPlaneJWTAuthenticationRequired) {
            pauseIf("jwt");
            return super.controlPlaneJWTAuthenticationRequired(controlPlaneJWTAuthenticationRequired);
        }

        @Override
        public Configuration http2Enabled(Boolean http2Enabled) {
            pauseIf("http2");
            return super.http2Enabled(http2Enabled);
        }
    }

    // keeps the reflective parity check honest about which names it skipped
    @Test
    public void shouldExposeOnlyAccessorsTheParityCheckCovers() {
        Set<String> uncovered = new TreeSet<>();
        for (Class<?> type : new Class<?>[]{ControlPlaneAuthenticationSettings.class, ServerTlsSettings.class}) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0) {
                    try {
                        Configuration.class.getMethod(method.getName());
                    } catch (NoSuchMethodException e) {
                        uncovered.add(method.getName());
                    }
                }
            }
        }
        assertThat(uncovered, containsInAnyOrder("authenticationRequired", "signature"));
    }
}
