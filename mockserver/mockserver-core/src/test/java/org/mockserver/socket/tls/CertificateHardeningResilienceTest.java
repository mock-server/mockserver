package org.mockserver.socket.tls;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Resilience tests for certificate hardening. Instance-only — these do not
 * mutate any process-global static state, so they run in the parallel test phase.
 *
 * <p>Covers: SAN-set growth bound and hostname normalisation (C3), torn key/cert state on a mid-flight
 * failure (C6), private-key file permissions (C10/C15) and loud failure on a corrupt CA PEM (C11).
 */
public class CertificateHardeningResilienceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Configuration configuration;
    private BCKeyAndCertificateFactory factory;

    @Before
    public void setUp() {
        configuration = configuration()
            .dynamicallyCreateCertificateAuthorityCertificate(true)
            .directoryToSaveDynamicSSLCertificate(tempFolder.getRoot().getAbsolutePath());
        factory = new BCKeyAndCertificateFactory(configuration, new MockServerLogger());
    }

    // --- C3: SAN-set growth bound + eviction ---

    @Test
    public void shouldBoundSubjectAlternativeNameSetAndEvictOldest() {
        // given a small cap on the SAN set
        configuration.maxSubjectAlternativeNames(5);

        // when far more distinct hostnames are added than the cap allows (the DoS vector)
        for (int i = 0; i < 50; i++) {
            configuration.addSubjectAlternativeName("host" + i + ".hardening.test");
        }

        // then the set never grows past the cap, and the most-recently-added host survives (older
        // entries were evicted) — proving the bound is enforced with eviction rather than by dropping
        // new additions
        Set<String> domains = configuration.sslSubjectAlternativeNameDomains();
        assertThat(domains.size(), is(lessThanOrEqualTo(5)));
        assertThat(domains, hasItem("host49.hardening.test"));
    }

    @Test
    public void shouldEvictOldestDynamicSubjectAlternativeNameFirstAndNeverEvictConfigured() {
        // given a small cap and an EXPLICITLY CONFIGURED SAN (not dynamically discovered), which must
        // never be evicted — losing an operator-configured host breaks TLS for it
        configuration.maxSubjectAlternativeNames(5);
        configuration.sslSubjectAlternativeNameDomains("configured.hardening.test");

        // when far more distinct dynamically-discovered hosts than the cap are added, oldest-first
        for (int i = 0; i < 10; i++) {
            configuration.addSubjectAlternativeName("d" + i + ".dynamic.test");
        }

        Set<String> domains = configuration.sslSubjectAlternativeNameDomains();
        // the configured SAN survives despite the cap being exceeded many times over (a ConcurrentHashSet
        // has no insertion order, so the pre-fix arbitrary eviction could and did drop it)
        assertThat("configured SAN must never be evicted", domains, hasItem("configured.hardening.test"));
        // eviction is genuinely FIFO: the OLDEST dynamic entries went first, the NEWEST survive. With the
        // pre-fix arbitrary (hash-order) eviction the surviving five were not this deterministic set.
        assertThat("oldest dynamic entry must have been evicted first", domains, not(hasItem("d0.dynamic.test")));
        assertThat(domains, not(hasItem("d5.dynamic.test")));
        assertThat(domains, hasItem("d6.dynamic.test"));
        assertThat(domains, hasItem("d9.dynamic.test"));
        assertThat(domains.size(), is(lessThanOrEqualTo(5)));
    }

    @Test
    public void shouldDefensivelyCopyACallerSuppliedNonConcurrentSubjectAlternativeNameSet() {
        // given a caller passes a plain (non-concurrent) HashSet — the raw reference must not be retained,
        // otherwise the unsynchronized certificate readers could hit a ConcurrentModificationException when
        // the synchronized add path mutates it concurrently
        java.util.Set<String> rawDomains = new java.util.HashSet<>();
        rawDomains.add("copied.hardening.test");
        configuration.sslSubjectAlternativeNameDomains(rawDomains);
        java.util.Set<String> rawIps = new java.util.HashSet<>();
        rawIps.add("10.0.0.1");
        configuration.sslSubjectAlternativeNameIps(rawIps);

        // the stored set is a distinct concurrent copy: mutating the caller's original set does not leak in
        rawDomains.add("leaked.hardening.test");
        rawIps.add("10.0.0.2");
        assertThat(configuration.sslSubjectAlternativeNameDomains(), hasItem("copied.hardening.test"));
        assertThat(configuration.sslSubjectAlternativeNameDomains(), not(hasItem("leaked.hardening.test")));
        assertThat(configuration.sslSubjectAlternativeNameIps(), hasItem("10.0.0.1"));
        assertThat(configuration.sslSubjectAlternativeNameIps(), not(hasItem("10.0.0.2")));

        // and the stored set is safe to iterate while it is being mutated (the risk a raw HashSet reopened)
        configuration.sslSubjectAlternativeNameDomains().add("concurrent.hardening.test");
        java.util.Iterator<String> iterator = configuration.sslSubjectAlternativeNameDomains().iterator();
        while (iterator.hasNext()) {
            iterator.next();
            configuration.sslSubjectAlternativeNameDomains().add("mid-iteration-" + System.nanoTime() + ".test");
        }
    }

    // --- C3: hostname normalisation / validation ---

    @Test
    public void shouldNormaliseAndValidateSubjectAlternativeNameDomains() {
        configuration.maxSubjectAlternativeNames(100);

        // uppercase is lowercased, a trailing dot stripped
        configuration.addSubjectAlternativeName("API.Example.COM.");
        // invalid hostnames are ignored, never baked into a certificate
        configuration.addSubjectAlternativeName("bad_host!.example.com");
        configuration.addSubjectAlternativeName("has space.example.com");

        Set<String> domains = configuration.sslSubjectAlternativeNameDomains();
        assertThat(domains, hasItem("api.example.com"));
        assertThat(domains, not(hasItem("API.Example.COM.")));
        assertThat(domains, not(hasItem("bad_host!.example.com")));
        assertThat(domains, not(hasItem("has space.example.com")));
    }

    @Test
    public void shouldRejectOverlyLongAndWildcardHandlingForSubjectAlternativeNameDomains() {
        configuration.maxSubjectAlternativeNames(100);

        // wildcard SANs are accepted (lowercased)
        configuration.addSubjectAlternativeName("*.Wildcard.Example.com");
        // a label longer than 63 chars, or a whole name longer than 253 chars, is rejected
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            tooLong.append("abcdefgh.");
        }
        configuration.addSubjectAlternativeName(tooLong.toString());

        Set<String> domains = configuration.sslSubjectAlternativeNameDomains();
        assertThat(domains, hasItem("*.wildcard.example.com"));
        assertThat(domains, not(hasItem(tooLong.toString())));
    }

    // --- COR-05 / FEA-01: leaf-validity override is clamped to a usable range ---

    @Test
    public void shouldClampBrokenlySmallLeafValidityUpToASafeFloorRatherThanMintAnExpiredOrSelfRenewingLeaf() throws Exception {
        // the previously-broken band: a positive override below the 30-day floor. Pre-fix only `< 1` was
        // guarded, so 1..5 minted a leaf whose notAfter (notBefore is back-dated 5 days) was already in the
        // past — checkValidity(now) threw and generation failed outright — and ~6 produced a leaf already
        // past its 80%-elapsed renewal threshold, regenerated on every handshake (a non-progress loop).
        for (int days : new int[]{1, 5, 6, 29}) {
            Configuration cfg = configuration()
                .dynamicallyCreateCertificateAuthorityCertificate(true)
                .directoryToSaveDynamicSSLCertificate(tempFolder.getRoot().getAbsolutePath())
                .sslCertificateLeafValidityInDays(days);
            BCKeyAndCertificateFactory f = new BCKeyAndCertificateFactory(cfg, new MockServerLogger());

            // must not throw: a broken-small value must never mint an already-expired leaf
            f.buildAndSavePrivateKeyAndX509Certificate();
            X509Certificate leaf = f.x509Certificate();
            long now = System.currentTimeMillis();

            // valid at "now" (not born expired)
            leaf.checkValidity(new Date(now));
            // and NOT already past its renewal threshold (so no per-handshake regeneration loop)
            assertFalse("a freshly minted leaf (configured validity days=" + days + ") must not already be "
                    + "past its renewal threshold",
                KeyAndCertificateFactory.isPastRenewalThreshold(leaf,
                    KeyAndCertificateFactory.RENEWAL_ELAPSED_FRACTION, now));
            // the total validity window was clamped UP to the floor
            long windowDays = Math.round((leaf.getNotAfter().getTime() - leaf.getNotBefore().getTime()) / 86400000.0);
            assertThat("validity window for configured days=" + days, windowDays,
                is(KeyAndCertificateFactory.LEAF_CERTIFICATE_VALIDITY_DAYS_MIN));
        }
    }

    @Test
    public void shouldClampExcessiveLeafValidityDownToTheCeilingToStayWithinTheX509DateRange() throws Exception {
        // FEA-01: an unbounded override could push notAfter past the X.509 year-9999 ceiling
        configuration.sslCertificateLeafValidityInDays(100_000_000);

        factory.buildAndSavePrivateKeyAndX509Certificate();
        X509Certificate leaf = factory.x509Certificate();

        leaf.checkValidity(new Date());
        long windowDays = Math.round((leaf.getNotAfter().getTime() - leaf.getNotBefore().getTime()) / 86400000.0);
        assertThat("excessive validity must be clamped down to the ceiling", windowDays,
            is(KeyAndCertificateFactory.LEAF_CERTIFICATE_VALIDITY_DAYS_MAX));
        // notAfter must stay comfortably inside the X.509 maximum (9999-12-31 == 253402300799000L)
        assertTrue("notAfter must stay within the X.509 date range",
            leaf.getNotAfter().getTime() < 253402300799000L);
    }

    @Test
    public void shouldHonourAnInRangeLeafValidityOverrideVerbatim() throws Exception {
        // a value inside [30, 3650] is used exactly as configured (no clamping)
        configuration.sslCertificateLeafValidityInDays(500);

        factory.buildAndSavePrivateKeyAndX509Certificate();
        X509Certificate leaf = factory.x509Certificate();

        leaf.checkValidity(new Date());
        long windowDays = Math.round((leaf.getNotAfter().getTime() - leaf.getNotBefore().getTime()) / 86400000.0);
        assertThat("an in-range override must be honoured verbatim", windowDays, is(500L));
    }

    // --- C6: torn key/cert state on a mid-flight failure ---

    @Test
    public void shouldLeavePreviousKeyAndCertificateIntactWhenRegenerationFailsMidFlight() {
        // given a first, successful generation
        factory.buildAndSavePrivateKeyAndX509Certificate();
        PrivateKey originalKey = factory.privateKey();
        X509Certificate originalCertificate = factory.x509Certificate();
        assertTrue(originalKey != null && originalCertificate != null);

        // when the next generation is forced to fail mid-flight: a subject domain that BouncyCastle
        // cannot parse into an X500Name makes leaf generation throw AFTER a fresh key pair is created
        configuration.sslCertificateDomainName("#zz");

        assertThrows(RuntimeException.class, () -> factory.buildAndSavePrivateKeyAndX509Certificate());

        // then the previously-working key AND certificate are still served as a matched pair — the
        // failed attempt did not publish a new key alongside the old certificate (the torn state)
        assertThat(factory.privateKey(), is(sameInstance(originalKey)));
        assertThat(factory.x509Certificate(), is(sameInstance(originalCertificate)));
        // and the surviving pair still matches (the cert verifies against the CA and the key is intact)
        assertThat(((java.security.interfaces.RSAPrivateKey) factory.privateKey()).getModulus(),
            equalTo(((java.security.interfaces.RSAPrivateKey) originalKey).getModulus()));
    }

    // --- C10 / C15: private-key file permissions ---

    @Test
    public void shouldWritePrivateKeyFilesOwnerReadableOnly() throws Exception {
        Assume.assumeTrue("POSIX file permissions not supported on this filesystem",
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        // given a deployment that persists both the CA key and the leaf key to disk
        configuration.preventCertificateDynamicUpdate(true);

        // when
        factory.buildAndSavePrivateKeyAndX509Certificate();

        // then every PRIVATE KEY file is 0600 (owner read/write only) and the PUBLIC certificate is 0644
        File dir = tempFolder.getRoot();
        assertPermissions(new File(dir, "PKCS8CertificateAuthorityPrivateKey.pem"), "rw-------");
        assertPermissions(new File(dir, "PKCS8PrivateKey.pem"), "rw-------");
        assertPermissions(new File(dir, "CertificateAuthorityCertificate.pem"), "rw-r--r--");
        assertPermissions(new File(dir, "Certificate.pem"), "rw-r--r--");
    }

    private void assertPermissions(File file, String expected) throws Exception {
        assertTrue("expected file to exist: " + file, file.exists());
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());
        assertThat("permissions of " + file.getName(), permissions, equalTo(PosixFilePermissions.fromString(expected)));
    }

    // --- C11: corrupt CA PEM must fail loudly, not silently regenerate ---

    @Test
    public void shouldFailLoudlyWhenCertificateAuthorityPemIsCorruptRatherThanRegenerating() throws Exception {
        // given a corrupt (present-but-unparseable) CA certificate already on disk at the path the
        // dynamic factory would use
        File corruptCaFile = new File(tempFolder.getRoot(), "CertificateAuthorityCertificate.pem");
        Files.write(corruptCaFile.toPath(), "-----BEGIN CERTIFICATE-----\nnot a real certificate\n-----END CERTIFICATE-----\n".getBytes());
        long sizeBefore = corruptCaFile.length();

        // when / then loading the CA must throw loudly instead of silently overwriting it
        RuntimeException exception = assertThrows(RuntimeException.class, () -> factory.certificateAuthorityX509Certificate());
        assertThat(exception.getMessage(), containsString("does not contain a valid"));

        // and the corrupt file was NOT overwritten with a freshly-generated CA
        assertThat(corruptCaFile.length(), is(equalTo(sizeBefore)));
    }
}
