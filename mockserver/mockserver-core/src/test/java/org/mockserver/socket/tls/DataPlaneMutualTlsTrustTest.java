package org.mockserver.socket.tls;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.mockserver.test.TestCertificates;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;
import static org.mockserver.file.FileReader.readFileFromClassPathOrPath;

/**
 * Data-plane mTLS never trusts the CA bundled with MockServer, whose private key ships in the jar, unless
 * tlsMutualAuthenticationAllowBundledCa is enabled.
 */
public class DataPlaneMutualTlsTrustTest {

    private static final String OWN_CA = "org/mockserver/authentication/mtls/ca.pem";
    private static final String OWN_CA_PRIVATE_KEY = "org/mockserver/authentication/mtls/ca-key-pkcs8.pem";

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldRefuseToStartWithMutualTlsWhenMockServerCaIsTheBundledCa() {
        DataPlaneMutualTlsConfigurationException exception = assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa(), mockServerLogger));

        assertThat(exception.getMessage(), is(DataPlaneMutualTlsConfigurationException.MESSAGE));
        assertThat(exception.getMessage(), allOf(
            containsString("certificateAuthorityCertificate and certificateAuthorityPrivateKey"),
            containsString("dynamicallyCreateCertificateAuthorityCertificate=true"),
            containsString("tlsMutualAuthenticationCertificateChain"),
            containsString("tlsMutualAuthenticationAllowBundledCa=true")
        ));
    }

    @Test
    public void shouldRefuseToStartWhenMockServerCaIsACopyOfTheBundledCa() throws IOException {
        File copy = temporaryFolder.newFile();
        Files.write(copy.toPath(), readFileFromClassPathOrPath(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE).getBytes(StandardCharsets.UTF_8));

        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().certificateAuthorityCertificate(copy.getAbsolutePath()), mockServerLogger));
    }

    @Test
    public void shouldRefuseToStartWithChainThatContainsTheBundledCa() throws IOException {
        String chain = chainOf(OWN_CA, DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);

        DataPlaneMutualTlsConfigurationException exception = assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationCertificateChain(chain), mockServerLogger));

        assertThat(exception.getMessage(), is(DataPlaneMutualTlsConfigurationException.BUNDLED_CA_IN_CHAIN_MESSAGE));
    }

    @Test
    public void shouldRefuseChainWithBundledCaEvenWhenMutualTlsIsNotRequired() throws IOException {
        String chain = chainOf(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);

        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationCertificateChain(chain), mockServerLogger));
    }

    @Test
    public void shouldRefuseCertificateReIssuedForTheBundledCaKey() throws Exception {
        TestCertificates.Issued bundled = TestCertificates.issuer(PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE), PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY));
        X509Certificate reIssued = TestCertificates.certificateAuthority("re-issued for the bundled key", bundled.keyPair).certificate;
        File reIssuedPem = temporaryFolder.newFile();
        Files.write(reIssuedPem.toPath(), TestCertificates.toPem(reIssued).getBytes(StandardCharsets.UTF_8));
        assertThat(reIssued, is(not(bundled.certificate)));

        // in the configured chain
        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationCertificateChain(reIssuedPem.getAbsolutePath()), mockServerLogger));
        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.clientCertificateTrust(reIssuedPem.getAbsolutePath(), PEMToFile.x509FromPEMFile(OWN_CA), false, null));
        // as MockServer's own CA certificate
        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().certificateAuthorityCertificate(reIssuedPem.getAbsolutePath()), mockServerLogger));
        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> DataPlaneMutualTlsTrust.clientCertificateTrust("", reIssued, false, null));
    }

    @Test
    public void shouldAllowBundledCaOnlyWithTheOptInAndWarn() throws IOException {
        CapturingLogger logger = new CapturingLogger();
        String chain = chainOf(OWN_CA, DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationAllowBundledCa(true), logger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationCertificateChain(chain).tlsMutualAuthenticationAllowBundledCa(true), logger);

        assertThat(logger.messages(Level.WARN), contains(
            containsString("ANYONE CAN CREATE A CLIENT CERTIFICATE MOCKSERVER ACCEPTS"),
            containsString("ANYONE CAN CREATE A CLIENT CERTIFICATE MOCKSERVER ACCEPTS")
        ));
        X509Certificate bundledCa = PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
        assertThat(DataPlaneMutualTlsTrust.clientCertificateTrust("", bundledCa, true, null), arrayContaining(bundledCa));
    }

    @Test
    public void shouldWarnWithTheOptInWhenAChainWithoutTheBundledCaIsSetAndMockServerCaIsTheBundledCa() {
        CapturingLogger logger = new CapturingLogger();

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationCertificateChain(OWN_CA).tlsMutualAuthenticationAllowBundledCa(true), logger);

        assertThat(logger.messages(Level.WARN), contains(containsString("ANYONE CAN CREATE A CLIENT CERTIFICATE MOCKSERVER ACCEPTS")));
        X509Certificate bundledCa = PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
        assertThat(DataPlaneMutualTlsTrust.clientCertificateTrust(OWN_CA, bundledCa, true, null), hasItemInArray(bundledCa));
    }

    @Test
    public void shouldNotWarnWithTheOptInWhenMutualTlsIsOff() {
        CapturingLogger logger = new CapturingLogger();

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationAllowBundledCa(true), logger);

        assertThat(logger.messages(Level.WARN), empty());
    }

    @Test
    public void shouldWarnWithTheOptInWhenAnUpdateMakesMutualTlsTrustTheBundledCa() {
        CapturingLogger logger = new CapturingLogger();
        Configuration withoutMutualTls = withBundledCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationAllowBundledCa(true);

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setMaxExpectations(10), withoutMutualTls, logger);
        assertThat(bundledCaTrustedWarnings(logger), is(0L));

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setTlsMutualAuthenticationRequired(true), withoutMutualTls, logger);
        assertThat(bundledCaTrustedWarnings(logger), is(1L));

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setCertificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE),
            withOwnCa().tlsMutualAuthenticationAllowBundledCa(true), logger);
        assertThat(bundledCaTrustedWarnings(logger), is(2L));

        // already trusted before the update, so nothing changes
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setMaxExpectations(10), withBundledCa().tlsMutualAuthenticationAllowBundledCa(true), logger);
        assertThat(bundledCaTrustedWarnings(logger), is(2L));
    }

    @Test
    public void shouldWarnWithTheOptInWhenTheServerTlsContextIsRebuiltToTrustTheBundledCa() throws IOException {
        CapturingLogger logger = new CapturingLogger();
        Configuration configuration = withBundledCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationAllowBundledCa(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, logger, true);
        factory.createServerSslContext();
        assertThat(bundledCaTrustedWarnings(logger), is(0L));

        configuration.tlsMutualAuthenticationRequired(true);
        factory.createServerSslContext();
        factory.createServerSslContext();

        assertThat("once per rebuild, not per handshake", bundledCaTrustedWarnings(logger), is(1L));
    }

    @Test
    public void shouldNotWarnWhenTheServerTlsContextIsRebuiltWithItsOwnCa() throws IOException {
        CapturingLogger logger = new CapturingLogger();
        Configuration configuration = withOwnCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationAllowBundledCa(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, logger, true);
        factory.createServerSslContext();

        configuration.tlsMutualAuthenticationRequired(true);
        factory.createServerSslContext();

        assertThat(bundledCaTrustedWarnings(logger), is(0L));
    }

    @Test
    public void shouldNotWarnWithTheOptInWhenTheBundledCaIsNotTrusted() {
        CapturingLogger logger = new CapturingLogger();

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationAllowBundledCa(true), logger);

        assertThat(logger.messages(Level.WARN), empty());
    }

    @Test
    public void shouldAcceptUsableMutualTlsSetUps() throws IOException {
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa(), mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().dynamicallyCreateCertificateAuthorityCertificate(true), mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationCertificateChain(OWN_CA), mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationRequired(false), mockServerLogger);
    }

    @Test
    public void shouldWarnThatMockServerCaIsLeftOutWhenChainIsSetAndMockServerCaIsTheBundledCa() {
        CapturingLogger logger = new CapturingLogger();

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withBundledCa().tlsMutualAuthenticationCertificateChain(OWN_CA), logger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(withOwnCa().tlsMutualAuthenticationCertificateChain(OWN_CA), logger);

        assertThat(logger.messages(Level.WARN), contains(containsString("mTLS trusts only tlsMutualAuthenticationCertificateChain")));
    }

    @Test
    public void shouldTrustOnlyTheChainWhenMockServerCaIsTheBundledCa() {
        X509Certificate[] trusted = DataPlaneMutualTlsTrust.clientCertificateTrust(OWN_CA, PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE), false, null);

        assertThat(trusted, arrayContaining(PEMToFile.x509ChainFromPEMFile(OWN_CA).toArray()));
    }

    @Test
    public void shouldTrustTheChainAndMockServerCaWhenMockServerCaIsNotTheBundledCa() throws Exception {
        X509Certificate dynamicCa = TestCertificates.certificateAuthority("dynamic").certificate;

        X509Certificate[] trusted = DataPlaneMutualTlsTrust.clientCertificateTrust(OWN_CA, dynamicCa, false, null);

        assertThat(trusted, arrayContaining(PEMToFile.x509FromPEMFile(OWN_CA), dynamicCa));
        assertThat(DataPlaneMutualTlsTrust.clientCertificateTrust("", dynamicCa, false, null), arrayContaining(dynamicCa));
    }

    @Test
    public void shouldRefuseToBuildTheServerTlsContextWhenItWouldTrustTheBundledCa() throws IOException {
        NettySslContextFactory factory = new NettySslContextFactory(withBundledCa().directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath()), mockServerLogger, true);

        DataPlaneMutualTlsConfigurationException exception = assertThrows(DataPlaneMutualTlsConfigurationException.class, factory::createServerSslContext);

        assertThat(exception.getMessage(), is(DataPlaneMutualTlsConfigurationException.MESSAGE));
    }

    @Test
    public void shouldRefuseToRebuildTheServerTlsContextAfterARuntimeChange() throws IOException {
        Configuration configuration = withBundledCa().tlsMutualAuthenticationRequired(false)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, mockServerLogger, true);
        assertThat(factory.createServerSslContext(), is(notNullValue()));

        configuration.tlsMutualAuthenticationRequired(true);

        assertThrows(DataPlaneMutualTlsConfigurationException.class, factory::createServerSslContext);
    }

    @Test
    public void shouldBuildTheServerTlsContextWithTheOptIn() throws IOException {
        Configuration configuration = withBundledCa().tlsMutualAuthenticationAllowBundledCa(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath());

        assertThat(new NettySslContextFactory(configuration, mockServerLogger, true).createServerSslContext(), is(notNullValue()));
    }

    @Test
    public void shouldRefuseAnUpdateThatMakesMutualTlsTrustTheBundledCa() throws IOException {
        Configuration withoutMutualTls = withBundledCa().tlsMutualAuthenticationRequired(false);
        String chain = chainOf(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);

        assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(
            new ConfigurationDTO().setTlsMutualAuthenticationRequired(true), withoutMutualTls, mockServerLogger));
        assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(
            new ConfigurationDTO().setTlsMutualAuthenticationCertificateChain(chain), withOwnCa(), mockServerLogger));
        assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(
            new ConfigurationDTO().setCertificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE), withOwnCa(), mockServerLogger));
        assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(
            new ConfigurationDTO().setDynamicallyCreateCertificateAuthorityCertificate(false), withBundledCa().dynamicallyCreateCertificateAuthorityCertificate(true), mockServerLogger));
    }

    @Test
    public void shouldNotLetAnUpdateEnableTheOptIn() {
        assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(
            new ConfigurationDTO().setTlsMutualAuthenticationRequired(true).setTlsMutualAuthenticationAllowBundledCa(true), withBundledCa().tlsMutualAuthenticationRequired(false), mockServerLogger));

        Configuration target = withBundledCa().tlsMutualAuthenticationRequired(false);
        new ConfigurationDTO().setTlsMutualAuthenticationAllowBundledCa(true).applyTo(target);
        assertThat(target.tlsMutualAuthenticationAllowBundledCa(), is(false));
    }

    @Test
    public void shouldAcceptAnUpdateThatLeavesUsableMutualTls() {
        Configuration withoutMutualTls = withBundledCa().tlsMutualAuthenticationRequired(false);

        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setTlsMutualAuthenticationRequired(true).setDynamicallyCreateCertificateAuthorityCertificate(true), withoutMutualTls, mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setTlsMutualAuthenticationRequired(true).setTlsMutualAuthenticationCertificateChain(OWN_CA), withoutMutualTls, mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setMaxExpectations(10), withoutMutualTls, mockServerLogger);
        DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setTlsMutualAuthenticationRequired(true), withBundledCa().tlsMutualAuthenticationRequired(false).tlsMutualAuthenticationAllowBundledCa(true), mockServerLogger);
    }

    private static long bundledCaTrustedWarnings(CapturingLogger logger) {
        return logger.messages(Level.WARN).stream().filter(message -> message.contains("ANYONE CAN CREATE A CLIENT CERTIFICATE MOCKSERVER ACCEPTS")).count();
    }

    private static Configuration withBundledCa() {
        return configuration()
            .tlsMutualAuthenticationRequired(true)
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY)
            .tlsMutualAuthenticationAllowBundledCa(false)
            .proactivelyInitialiseTLS(false);
    }

    private static Configuration withOwnCa() {
        return withBundledCa()
            .certificateAuthorityCertificate(OWN_CA)
            .certificateAuthorityPrivateKey(OWN_CA_PRIVATE_KEY);
    }

    private static String chainOf(String... certificates) throws IOException {
        StringBuilder pem = new StringBuilder();
        for (String certificate : certificates) {
            pem.append(readFileFromClassPathOrPath(certificate)).append('\n');
        }
        File chain = temporaryFolder.newFile();
        Files.write(chain.toPath(), pem.toString().getBytes(StandardCharsets.UTF_8));
        return chain.getAbsolutePath();
    }

    private static class CapturingLogger extends MockServerLogger {
        private final List<LogEntry> logged = new CopyOnWriteArrayList<>();

        CapturingLogger() {
            super(configuration().logLevel("INFO"), DataPlaneMutualTlsTrustTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            logged.add(logEntry);
        }

        List<String> messages(Level level) {
            return logged.stream()
                .filter(logEntry -> logEntry.getLogLevel() == level)
                .map(LogEntry::getMessage)
                .collect(Collectors.toList());
        }
    }
}
