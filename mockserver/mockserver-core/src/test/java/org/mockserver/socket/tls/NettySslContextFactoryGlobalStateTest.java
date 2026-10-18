package org.mockserver.socket.tls;

import io.netty.handler.ssl.SslContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Global-state coverage for two TLS-hardening behaviours whose evidence requires mutating a
 * process-wide static:
 * <ul>
 *   <li>The once-per-JVM bundled-CA WARN latch ({@link NettySslContextFactory#BUNDLED_CA_WARNING_LOGGED}).</li>
 *   <li>The time-bounded re-check of a user-supplied FIXED server certificate, driven by the
 *       overridable {@link NettySslContextFactory#fixedCertificateClock}.</li>
 * </ul>
 * Registered in the sequential (non-parallel) surefire lane because both the latch and the clock are
 * shared static state that concurrent factory construction in other suites would perturb.
 */
public class NettySslContextFactoryGlobalStateTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LongSupplier originalFixedCertificateClock;

    @Before
    public void captureClock() {
        originalFixedCertificateClock = NettySslContextFactory.fixedCertificateClock;
    }

    @After
    public void restoreState() {
        NettySslContextFactory.fixedCertificateClock = originalFixedCertificateClock;
        NettySslContextFactory.BUNDLED_CA_WARNING_LOGGED.set(false);
    }

    // ---- bundled default CA WARN, once per JVM ----

    @Test
    public void shouldWarnOnceWhenBundledDefaultCertificateAuthorityInUse() {
        NettySslContextFactory.BUNDLED_CA_WARNING_LOGGED.set(false);

        CapturingLogger firstLogger = new CapturingLogger();
        new NettySslContextFactory(configuration(), firstLogger, true);
        assertThat("bundled-CA WARN should fire for the first factory using the default CA",
            bundledCaWarnings(firstLogger), hasSize(1));

        CapturingLogger secondLogger = new CapturingLogger();
        new NettySslContextFactory(configuration(), secondLogger, true);
        assertThat("bundled-CA WARN must be latched and not fire a second time",
            bundledCaWarnings(secondLogger), is(empty()));
    }

    @Test
    public void shouldNotWarnAboutBundledCaWhenDynamicCaEnabled() {
        NettySslContextFactory.BUNDLED_CA_WARNING_LOGGED.set(false);

        CapturingLogger logger = new CapturingLogger();
        new NettySslContextFactory(configuration().dynamicallyCreateCertificateAuthorityCertificate(true), logger, true);

        assertThat(bundledCaWarnings(logger), is(empty()));
    }

    @Test
    public void shouldNotWarnAboutBundledCaFromClientOnlyFactory() {
        // OPS-02: the bundled CA signs SERVED (inbound) traffic only. A client-only factory presents no
        // server certificate, so the server-oriented "signing TLS traffic with the bundled default CA"
        // warning would be inaccurate and misleading in a client-only JVM — it must not fire when
        // forServer=false, even though the default CA configuration would otherwise trip the detector.
        NettySslContextFactory.BUNDLED_CA_WARNING_LOGGED.set(false);

        CapturingLogger logger = new CapturingLogger();
        new NettySslContextFactory(configuration(), logger, false);

        assertThat(bundledCaWarnings(logger), is(empty()));
        // and the latch is untouched, so a subsequent server factory still warns
        assertThat(NettySslContextFactory.BUNDLED_CA_WARNING_LOGGED.get(), is(false));

        CapturingLogger serverLogger = new CapturingLogger();
        new NettySslContextFactory(configuration(), serverLogger, true);
        assertThat(bundledCaWarnings(serverLogger), hasSize(1));
    }

    // ---- fixed server certificate re-check ----

    @Test
    public void shouldRebuildServerContextWhenFixedCertificateRotatedOnDisk() throws Exception {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        NettySslContextFactory.fixedCertificateClock = clock::get;

        String[] keyAndCert = generateKeyAndCert(new Date(System.currentTimeMillis() - 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365));
        File keyFile = writePem("key.pem", keyAndCert[0]);
        File certFile = writePem("cert.pem", keyAndCert[1]);
        Configuration configuration = fixedCertificateConfiguration(keyFile, certFile);

        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        SslContext first = factory.createServerSslContext();

        // rotate the cert/key pair on disk in place (paths, and therefore the context signature, unchanged)
        String[] rotated = generateKeyAndCert(new Date(System.currentTimeMillis() - 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365));
        overwrite(keyFile, rotated[0]);
        overwrite(certFile, rotated[1]);
        certFile.setLastModified(System.currentTimeMillis() + 3_600_000L);

        // advance past the re-check throttle so the on-disk change is observed
        clock.addAndGet(120_000L);
        SslContext second = factory.createServerSslContext();

        assertThat("a fixed certificate rotated on disk must force a rebuild rather than serving the stale one",
            second, not(sameInstance(first)));
    }

    @Test
    public void shouldLeaveTheFixedCertificateDiskCheckToTheProvisioningPathWhenItFallsDue() throws Exception {
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        NettySslContextFactory.fixedCertificateClock = clock::get;

        String[] keyAndCert = generateKeyAndCert(new Date(System.currentTimeMillis() - 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365));
        File keyFile = writePem("key.pem", keyAndCert[0]);
        File certFile = writePem("cert.pem", keyAndCert[1]);
        NettySslContextFactory factory = new NettySslContextFactory(fixedCertificateConfiguration(keyFile, certFile), new MockServerLogger(), true);
        SslContext first = factory.createServerSslContext();
        assertThat(factory.cachedServerSslContext(), sameInstance(first));

        String[] rotated = generateKeyAndCert(new Date(System.currentTimeMillis() - 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365));
        overwrite(keyFile, rotated[0]);
        overwrite(certFile, rotated[1]);
        certFile.setLastModified(System.currentTimeMillis() + 3_600_000L);
        clock.addAndGet(120_000L);

        // the event-loop check never touches the disk: a due re-check sends the caller to the provisioning pool
        assertThat(factory.cachedServerSslContext(), nullValue());
        SslContext second = factory.createServerSslContext();
        assertThat(second, not(sameInstance(first)));
        assertThat(factory.cachedServerSslContext(), sameInstance(second));
    }

    @Test
    public void shouldWarnWhenFixedCertificateExpiresWhileServing() throws Exception {
        long base = System.currentTimeMillis();
        AtomicLong clock = new AtomicLong(base);
        NettySslContextFactory.fixedCertificateClock = clock::get;

        // valid at build time (notAfter 2 days out), so CertificateConfigurationValidator accepts it
        String[] keyAndCert = generateKeyAndCert(new Date(base - 86400000L), new Date(base + 86400000L * 2));
        File keyFile = writePem("key.pem", keyAndCert[0]);
        File certFile = writePem("cert.pem", keyAndCert[1]);
        Configuration configuration = fixedCertificateConfiguration(keyFile, certFile);

        CapturingLogger logger = new CapturingLogger();
        NettySslContextFactory factory = new NettySslContextFactory(configuration, logger, true);
        SslContext first = factory.createServerSslContext();

        // advance the clock 3 days: past the served certificate's expiry (and the re-check throttle)
        clock.set(base + 86400000L * 3);
        SslContext second = factory.createServerSslContext();

        assertThat("an unchanged-but-expired fixed certificate is still served (cannot self-renew)",
            second, sameInstance(first));
        assertThat("but the expiry must be surfaced with a WARN rather than served silently",
            logger.warningsContaining("has expired"), hasSize(1));
    }

    private Configuration fixedCertificateConfiguration(File keyFile, File certFile) {
        // point the CA at the (self-signed) leaf so CertificateConfigurationValidator's leaf-signed-by-CA
        // check is satisfied without a separate CA
        return configuration()
            .privateKeyPath(keyFile.getAbsolutePath())
            .x509CertificatePath(certFile.getAbsolutePath())
            .certificateAuthorityCertificate(certFile.getAbsolutePath());
    }

    private static List<String> bundledCaWarnings(CapturingLogger logger) {
        return logger.warningsContaining("bundled default Certificate Authority");
    }

    private File writePem(String name, String content) throws IOException {
        File file = tempFolder.newFile(name);
        overwrite(file, content);
        return file;
    }

    private static void overwrite(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(content);
        }
    }

    private String[] generateKeyAndCert(Date notBefore, Date notAfter) throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        org.bouncycastle.asn1.x500.X500Name issuer = new org.bouncycastle.asn1.x500.X500Name("CN=localhost");
        // distinct serial each call so a rotation genuinely differs
        BigInteger serial = BigInteger.valueOf(System.nanoTime());

        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic()
        );

        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());

        X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer));

        return new String[]{
            pemEncode("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
            pemEncode("CERTIFICATE", cert.getEncoded())
        };
    }

    private static String pemEncode(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
            + "\n-----END " + type + "-----\n";
    }

    /**
     * Captures WARN message strings at log time (the {@link LogEntry} itself must not be retained — it is
     * recycled once handed to the disruptor).
     */
    private static class CapturingLogger extends MockServerLogger {
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        CapturingLogger() {
            // WARN so the warnings this test asserts on pass the level predicate the production
            // path applies. The suite default is ERROR, at which they are not logged.
            super(configuration().logLevel("WARN"), NettySslContextFactoryGlobalStateTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            if (logEntry.getLogLevel() == Level.WARN) {
                warnings.add(String.valueOf(logEntry.getMessage()));
            }
            super.logEvent(logEntry);
        }

        List<String> warningsContaining(String fragment) {
            List<String> matches = new java.util.ArrayList<>();
            for (String warning : warnings) {
                if (warning != null && warning.contains(fragment)) {
                    matches.add(warning);
                }
            }
            return matches;
        }
    }
}
