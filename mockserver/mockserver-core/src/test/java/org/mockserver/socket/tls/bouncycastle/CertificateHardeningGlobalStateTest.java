package org.mockserver.socket.tls.bouncycastle;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;
import org.mockserver.socket.tls.NettySslContextFactory;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.socket.tls.NettySslContextFactoryTestAccess.cachedServerSslContext;

/**
 * Certificate-hardening tests that drive process-global static seams — the renewal clock
 * ({@link BCKeyAndCertificateFactory#renewalClock}) and the custom {@code KeyAndCertificateFactory}
 * supplier — and so MUST run in the sequential test phase (registered in mockserver-core/pom.xml in
 * BOTH the parallel-excludes and sequential-includes lists).
 *
 * <p>Covers: expiry-driven regeneration (C1/C2 — the safety net for a later wave shortening the leaf
 * validity), the concurrent lost-update that returned a certificate missing a just-added SAN (C4), and
 * that concurrent provisioning of two new hosts yields a certificate containing both SANs (C4/C5).
 */
public class CertificateHardeningGlobalStateTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Configuration configuration;
    private BCKeyAndCertificateFactory sharedFactory;
    private LongSupplier originalRenewalClock;

    @Before
    public void setUp() {
        originalRenewalClock = BCKeyAndCertificateFactory.renewalClock;
        configuration = configuration()
            .dynamicallyCreateCertificateAuthorityCertificate(true)
            .directoryToSaveDynamicSSLCertificate(tempFolder.getRoot().getAbsolutePath());
        sharedFactory = new BCKeyAndCertificateFactory(configuration, new MockServerLogger());
        // make the NettySslContextFactory under test use the SAME factory instance so the test can
        // observe the exact leaf certificate that was baked into the served SslContext
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(
            (logger, forServer, config) -> sharedFactory);
    }

    @After
    public void tearDown() {
        BCKeyAndCertificateFactory.renewalClock = originalRenewalClock;
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
    }

    // --- C1 / C2: expiry-driven regeneration (the single most important new test) ---

    @Test
    public void shouldServeAFreshCertificateOnceTheLeafHasCrossedItsRenewalThreshold() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        // given an initial certificate is generated and cached
        SslContext firstContext = nettySslContextFactory.createServerSslContext();
        X509Certificate firstLeaf = sharedFactory.x509Certificate();
        assertTrue(firstLeaf != null);

        // a second call with an unexpired certificate returns the SAME cached context and leaf
        assertThat(nettySslContextFactory.createServerSslContext(), is(sameInstance(firstContext)));
        assertThat(sharedFactory.x509Certificate(), is(sameInstance(firstLeaf)));

        // when the clock advances past the leaf's expiry (simulating time passing, as a later wave that
        // shortens leaf validity would experience in normal running)
        BCKeyAndCertificateFactory.renewalClock = () -> firstLeaf.getNotAfter().getTime() + TimeUnit.DAYS.toMillis(1);
        assertTrue("factory should report the expired leaf needs renewal", sharedFactory.certificateNeedsRenewal());

        SslContext secondContext = nettySslContextFactory.createServerSslContext();
        X509Certificate secondLeaf = sharedFactory.x509Certificate();

        // then a genuinely FRESH certificate (new serial, new instance) is served, not the expired one
        assertThat(secondContext, is(not(sameInstance(firstContext))));
        assertThat(secondLeaf, is(not(sameInstance(firstLeaf))));
        assertThat(secondLeaf.getSerialNumber(), is(not(firstLeaf.getSerialNumber())));

        // and the fresh certificate is valid into the future relative to real (unmocked) time
        BCKeyAndCertificateFactory.renewalClock = originalRenewalClock;
        assertTrue("fresh leaf must not already be expired", secondLeaf.getNotAfter().after(new Date()));
    }

    // --- a near-expiry CA must NOT drive a permanent leaf-regeneration loop ---

    @Test
    public void shouldNotDemandRenewalRepeatedlyWhenOnlyTheCertificateAuthorityIsNearExpiry() throws Exception {
        // given a freshly generated leaf + CA
        sharedFactory.buildAndSavePrivateKeyAndX509Certificate();
        X509Certificate freshLeaf = sharedFactory.x509Certificate();
        assertTrue(freshLeaf != null);

        // pin the renewal clock to a fixed "now" so the assertions are deterministic
        long nowMillis = System.currentTimeMillis();
        BCKeyAndCertificateFactory.renewalClock = () -> nowMillis;

        // and force the cached CA to be one that has crossed its 80%-elapsed renewal threshold while the
        // leaf remains fresh — this is the real-world state after years of running, where the CA was minted
        // once and never regenerated (its guard short-circuits) but each leaf is re-minted fresh
        X509Certificate nearExpiryCa = certificateWithValidity(
            new Date(nowMillis - TimeUnit.DAYS.toMillis(365L * 10)),
            new Date(nowMillis + TimeUnit.DAYS.toMillis(365L))
        );
        assertTrue("test fixture invalid: CA should be past its renewal threshold",
            KeyAndCertificateFactory.isPastRenewalThreshold(nearExpiryCa, KeyAndCertificateFactory.RENEWAL_ELAPSED_FRACTION, nowMillis));
        assertFalse("test fixture invalid: fresh leaf should NOT be past its renewal threshold",
            KeyAndCertificateFactory.isPastRenewalThreshold(freshLeaf, KeyAndCertificateFactory.RENEWAL_ELAPSED_FRACTION, nowMillis));
        injectCertificateAuthority(sharedFactory, nearExpiryCa);

        // then the renewal trigger does NOT fire on the CA's account: the leaf is fresh, so no renewal is
        // demanded. Before the fix this returned true purely because of the CA, and since the CA guard
        // never regenerates the CA, every handshake re-minted the leaf forever (a non-progress loop).
        assertFalse("a near-expiry CA must not by itself demand leaf renewal", sharedFactory.certificateNeedsRenewal());
        // and the trigger stays cleared across repeated calls — it can never enter the loop
        for (int i = 0; i < 5; i++) {
            assertFalse("renewal trigger must remain cleared (no non-progress loop)", sharedFactory.certificateNeedsRenewal());
        }
    }

    /**
     * Build a throwaway self-signed certificate with an explicit validity window, so a test can place a CA
     * past (or before) its renewal threshold without waiting years of wall-clock time.
     */
    private static X509Certificate certificateWithValidity(Date notBefore, Date notAfter) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        X500Name name = new X500Name("CN=near-expiry-test-ca");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(System.nanoTime()), notBefore, notAfter, name, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static void injectCertificateAuthority(BCKeyAndCertificateFactory factory, X509Certificate certificate) throws Exception {
        Field field = BCKeyAndCertificateFactory.class.getDeclaredField("certificateAuthorityX509Certificate");
        field.setAccessible(true);
        field.set(factory, certificate);
    }

    // --- C4: lost-update — a cleared rebuild flag must not return a certificate missing a new SAN ---

    @Test
    public void shouldNotServeACertificateMissingAJustAddedSubjectAlternativeName() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        configuration.addSubjectAlternativeName("first.c4.test");
        nettySslContextFactory.createServerSslContext();
        assertThat(dnsNames(sharedFactory.x509Certificate()), hasItem("first.c4.test"));

        // add a second SAN, then simulate the C4 race directly: another thread consumed (cleared) the
        // single global rebuild flag after this SAN was added but before this thread rebuilds
        configuration.addSubjectAlternativeName("second.c4.test");
        configuration.rebuildServerTLSContext(false);

        // with the consumable-boolean guard this returned the stale context missing "second.c4.test";
        // the content signature must force a rebuild instead
        nettySslContextFactory.createServerSslContext();
        Set<String> dnsNames = dnsNames(sharedFactory.x509Certificate());
        assertThat(dnsNames, hasItem("first.c4.test"));
        assertThat(dnsNames, hasItem("second.c4.test"));
    }

    // --- C4 / C5: concurrent provisioning of two new hosts yields a cert containing BOTH SANs ---

    @Test
    public void shouldIncludeBothSubjectAlternativeNamesWhenTwoNewHostsProvisionConcurrently() throws Exception {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        int iterations = 25;
        for (int i = 0; i < iterations; i++) {
            String hostA = "a" + i + ".concurrent.test";
            String hostB = "b" + i + ".concurrent.test";
            CountDownLatch start = new CountDownLatch(1);
            Set<Throwable> failures = ConcurrentHashMap.newKeySet();

            Runnable provision = () -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            Thread threadA = new Thread(() -> {
                provision.run();
                try {
                    configuration.addSubjectAlternativeName(hostA);
                    nettySslContextFactory.createServerSslContext();
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            Thread threadB = new Thread(() -> {
                provision.run();
                try {
                    configuration.addSubjectAlternativeName(hostB);
                    nettySslContextFactory.createServerSslContext();
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            threadA.start();
            threadB.start();
            start.countDown();
            threadA.join(TimeUnit.SECONDS.toMillis(30));
            threadB.join(TimeUnit.SECONDS.toMillis(30));

            assertTrue("provisioning threw: " + failures, failures.isEmpty());
            // a final provisioning settles the certificate on the full SAN set
            nettySslContextFactory.createServerSslContext();
            Set<String> dnsNames = dnsNames(sharedFactory.x509Certificate());
            assertThat("iteration " + i, dnsNames, hasItem(hostA));
            assertThat("iteration " + i, dnsNames, hasItem(hostB));
        }
    }

    // --- the non-blocking cached-context check SniHandler runs on the event loop ---

    @Test
    public void shouldOfferTheCachedContextWithoutProvisioningOnlyWhileEveryInputIsUnchanged() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        assertThat("nothing is cached before the first build", cachedServerSslContext(nettySslContextFactory), is(nullValue()));

        SslContext first = nettySslContextFactory.createServerSslContext();
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(first)));

        // a setter that re-applies the same value moves the modification count but not the inputs
        configuration.tlsProtocols(configuration.tlsProtocols());
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(first)));
        assertThat(nettySslContextFactory.createServerSslContext(), is(sameInstance(first)));

        // a new dynamic SAN
        configuration.addSubjectAlternativeName("new-host.fast-path.test");
        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        SslContext second = nettySslContextFactory.createServerSslContext();
        assertThat(second, is(not(sameInstance(first))));
        assertThat(dnsNames(sharedFactory.x509Certificate()), hasItem("new-host.fast-path.test"));
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(second)));

        // replacing the configured SAN set wholesale
        configuration.sslSubjectAlternativeNameDomains(Set.of("localhost", "replaced.fast-path.test"));
        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        SslContext third = nettySslContextFactory.createServerSslContext();
        assertThat(dnsNames(sharedFactory.x509Certificate()), hasItem("replaced.fast-path.test"));

        // every non-SAN input baked into the context
        configuration.http2Enabled(!configuration.http2Enabled());
        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        SslContext fourth = nettySslContextFactory.createServerSslContext();
        assertThat(fourth, is(not(sameInstance(third))));
        configuration.tlsProtocols("TLSv1.2");
        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        SslContext fifth = nettySslContextFactory.createServerSslContext();
        configuration.tlsMutualAuthenticationRequired(!configuration.tlsMutualAuthenticationRequired());
        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        SslContext sixth = nettySslContextFactory.createServerSslContext();
        assertThat(sixth, is(not(sameInstance(fifth))));
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(sixth)));
    }

    @Test
    public void shouldNotOfferTheCachedContextOnceTheLeafNeedsRenewal() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        SslContext first = nettySslContextFactory.createServerSslContext();
        X509Certificate firstLeaf = sharedFactory.x509Certificate();
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(first)));

        BCKeyAndCertificateFactory.renewalClock = () -> firstLeaf.getNotAfter().getTime() + TimeUnit.DAYS.toMillis(1);

        assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
        assertThat(nettySslContextFactory.createServerSslContext(), is(not(sameInstance(first))));
    }

    @Test
    public void shouldStopRenewingOnceTheRenewedLeafIsFresh() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        nettySslContextFactory.createServerSslContext();
        X509Certificate firstLeaf = sharedFactory.x509Certificate();

        // the renewed leaf gets a far longer life, so the advanced clock is past the first leaf's
        // threshold but well inside the renewed one's
        configuration.sslCertificateLeafValidityInDays(3650);
        BCKeyAndCertificateFactory.renewalClock = () -> firstLeaf.getNotAfter().getTime() + TimeUnit.DAYS.toMillis(1);
        SslContext renewed = nettySslContextFactory.createServerSslContext();

        assertThat(sharedFactory.x509Certificate(), is(not(sameInstance(firstLeaf))));
        assertFalse("the renewal decision must follow the current leaf, not the one it replaced", sharedFactory.certificateNeedsRenewal());
        assertThat(cachedServerSslContext(nettySslContextFactory), is(sameInstance(renewed)));
        assertThat(nettySslContextFactory.createServerSslContext(), is(sameInstance(renewed)));
    }

    @Test
    public void shouldRebuildWhenASubjectAlternativeNameArrivesWhileTheContextIsBeingBuilt() {
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        UnaryOperator<SslContextBuilder> originalCustomizer = NettySslContextFactory.sslServerContextBuilderCustomizer;
        AtomicBoolean added = new AtomicBoolean();
        // runs inside the build after the leaf is minted: the window in which a concurrent SNI handshake
        // for a new host adds its SAN
        NettySslContextFactory.sslServerContextBuilderCustomizer = builder -> {
            if (added.compareAndSet(false, true)) {
                configuration.addSubjectAlternativeName("mid-build.race.test");
            }
            return builder;
        };
        try {
            SslContext first = nettySslContextFactory.createServerSslContext();
            assertTrue(added.get());
            assertThat(dnsNames(sharedFactory.x509Certificate()), not(hasItem("mid-build.race.test")));

            assertThat(cachedServerSslContext(nettySslContextFactory), is(nullValue()));
            SslContext second = nettySslContextFactory.createServerSslContext();

            assertThat(second, is(not(sameInstance(first))));
            assertThat(dnsNames(sharedFactory.x509Certificate()), hasItem("mid-build.race.test"));
        } finally {
            NettySslContextFactory.sslServerContextBuilderCustomizer = originalCustomizer;
        }
    }

    private Set<String> dnsNames(X509Certificate certificate) {
        try {
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans == null) {
                return Set.of();
            }
            return sans.stream()
                .filter(san -> Integer.valueOf(2).equals(san.get(0)))
                .map(san -> (String) san.get(1))
                .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException("failed to read SANs", e);
        }
    }
}
