package org.mockserver.authentication;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.JDKCertificateToMockServerX509Certificate;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TestCertificates;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
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
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.socket.tls.KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory;

/**
 * Which client certificates control-plane mTLS accepts. With controlPlaneTLSMutualAuthenticationCAChain set, only
 * those signed by that chain; without it, those signed by MockServer's CA, unless that is the bundled CA.
 */
public class ControlPlaneMutualTlsClientCertificateTrustTest {

    private static final String CONTROL_PLANE_CA_CHAIN = "org/mockserver/authentication/mtls/ca.pem";
    private static final String CONTROL_PLANE_CA_PRIVATE_KEY = "org/mockserver/authentication/mtls/ca-key-pkcs8.pem";
    // signed by CONTROL_PLANE_CA_CHAIN
    private static final String CONTROL_PLANE_LEAF = "org/mockserver/authentication/mtls/leaf-cert.pem";

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldRejectClientCertificateSignedOnlyByMockServerCaWhenChainIsSet() throws IOException {
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(withBundledCa().controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN), mockServerLogger);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> handler.controlPlaneRequestAuthenticated(requestPresenting(signedByBundledCa())));

        assertThat(exception.getMessage(), containsString("no client certificates can be validated by control plane CA"));
    }

    @Test
    public void shouldAcceptClientCertificateSignedByChain() {
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(withBundledCa().controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN), mockServerLogger);

        assertThat(handler.controlPlaneRequestAuthenticated(requestPresenting(PEMToFile.x509FromPEMFile(CONTROL_PLANE_LEAF))), is(true));
    }

    @Test
    public void shouldTrustOnlyTheChainWhenChainIsSet() {
        X509Certificate[] trusted = ControlPlaneAuthenticationHandlerFactory.controlPlaneClientCertificateTrust(CONTROL_PLANE_CA_CHAIN, withBundledCa(), mockServerLogger);

        assertThat(trusted, arrayContaining(PEMToFile.x509ChainFromPEMFile(CONTROL_PLANE_CA_CHAIN).toArray()));
    }

    @Test
    public void shouldDenyEveryRequestWhenNoChainIsSetAndMockServerCaIsTheBundledCa() throws IOException {
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(withBundledCa().controlPlaneTLSMutualAuthenticationCAChain(""), mockServerLogger);

        assertThat(handler, instanceOf(ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler.class));
        assertThrows(AuthenticationException.class, () -> handler.controlPlaneRequestAuthenticated(requestPresenting(signedByBundledCa())));
    }

    @Test
    public void shouldTrustMockServerCaWhenNoChainIsSetAndMockServerCaIsNotTheBundledCa() {
        Configuration configuration = mutualTlsServer()
            .certificateAuthorityCertificate(CONTROL_PLANE_CA_CHAIN)
            .certificateAuthorityPrivateKey(CONTROL_PLANE_CA_PRIVATE_KEY)
            .controlPlaneTLSMutualAuthenticationCAChain("");

        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(configuration, mockServerLogger);

        assertThat(handler.controlPlaneRequestAuthenticated(requestPresenting(PEMToFile.x509FromPEMFile(CONTROL_PLANE_LEAF))), is(true));
    }

    @Test
    public void shouldRefuseChainThatIncludesBundledCa() throws IOException {
        String chain = chainWithBundledCa();

        ControlPlaneMutualTlsConfigurationException exception = assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.controlPlaneClientCertificateTrust(chain, withBundledCa(), mockServerLogger));

        assertThat(exception.getMessage(), is(ControlPlaneMutualTlsConfigurationException.BUNDLED_CA_IN_CHAIN_MESSAGE));
    }

    @Test
    public void shouldRefuseToStartWithChainThatIncludesBundledCa() throws IOException {
        String chain = chainWithBundledCa();

        ControlPlaneMutualTlsConfigurationException exception = assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(chain)));

        assertThat(exception.getMessage(), is(ControlPlaneMutualTlsConfigurationException.BUNDLED_CA_IN_CHAIN_MESSAGE));
    }

    @Test
    public void shouldRefuseAnUpdateThatPutsBundledCaInTheChain() throws IOException {
        String chain = chainWithBundledCa();

        assertThrows(ControlPlaneMutualTlsConfigurationException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationCAChain(chain), withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN)));
        assertThrows(ControlPlaneMutualTlsConfigurationException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationRequired(true), withOwnCa().controlPlaneTLSMutualAuthenticationRequired(false).controlPlaneTLSMutualAuthenticationCAChain(chain)));
    }

    @Test
    public void shouldDenyEveryRequestWhenChainIncludesBundledCa() throws Exception {
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(chainWithBundledCa()), mockServerLogger);

        assertThat(handler, instanceOf(ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler.class));
        assertThrows(AuthenticationException.class, () -> handler.controlPlaneRequestAuthenticated(requestPresenting(signedByBundledCa())));
        assertThrows(AuthenticationException.class, () -> handler.controlPlaneRequestAuthenticated(requestPresenting(PEMToFile.x509FromPEMFile(CONTROL_PLANE_LEAF))));
    }

    @Test
    public void shouldRefuseChainThatIncludesACertificateReIssuedForTheBundledCaKey() throws Exception {
        TestCertificates.Issued bundled = TestCertificates.issuer(PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE), PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY));
        X509Certificate reIssued = TestCertificates.certificateAuthority("re-issued for the bundled key", bundled.keyPair).certificate;
        File chain = temporaryFolder.newFile();
        Files.write(chain.toPath(), TestCertificates.toPem(reIssued).getBytes(StandardCharsets.UTF_8));

        assertThat(reIssued, is(not(bundled.certificate)));
        assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(chain.getAbsolutePath())));
        assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.controlPlaneClientCertificateTrust(chain.getAbsolutePath(), withOwnCa(), mockServerLogger));
    }

    @Test
    public void shouldRefuseChainThatIncludesTheBundledCaKeyUnderAnotherRsaAlgorithmIdentifier() throws Exception {
        X509Certificate bundledCa = PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
        PrivateKey bundledKey = PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
        java.security.interfaces.RSAPublicKey rsa = (java.security.interfaces.RSAPublicKey) bundledCa.getPublicKey();
        byte[] rsaKey = new org.bouncycastle.asn1.pkcs.RSAPublicKey(rsa.getModulus(), rsa.getPublicExponent()).getEncoded();
        List<AlgorithmIdentifier> algorithms = List.of(
            new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption),
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSASSA_PSS)
        );

        for (AlgorithmIdentifier algorithm : algorithms) {
            X509Certificate reEncoded = selfSignedCertificateAuthority(new SubjectPublicKeyInfo(algorithm, rsaKey), bundledKey);
            File chain = temporaryFolder.newFile();
            Files.write(chain.toPath(), TestCertificates.toPem(reEncoded).getBytes(StandardCharsets.UTF_8));
            X509Certificate[] parsed = NettySslContextFactory.controlPlaneTrustCertificates(chain.getAbsolutePath());

            if (PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algorithm.getAlgorithm())) {
                assertThat(parsed[0].getPublicKey().equals(bundledCa.getPublicKey()), is(false));
            }
            assertThat(algorithm.getAlgorithm().getId(), ControlPlaneAuthenticationHandlerFactory.containsBundledCertificateAuthority(parsed), is(true));
            assertThrows(ControlPlaneMutualTlsConfigurationException.class,
                () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(chain.getAbsolutePath())));
            assertThat(ControlPlaneAuthenticationHandlerFactory.build(withOwnCa().controlPlaneTLSMutualAuthenticationCAChain(chain.getAbsolutePath()), mockServerLogger),
                instanceOf(ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler.class));
        }
    }

    @Test
    public void shouldCompareEcKeysByCurveAndPoint() throws Exception {
        KeyPair first = TestCertificates.certificateAuthority("first").keyPair;
        KeyPair second = TestCertificates.certificateAuthority("second").keyPair;
        PublicKey firstReParsed = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(first.getPublic().getEncoded()));

        assertThat(ControlPlaneAuthenticationHandlerFactory.sameKeyMaterial(first.getPublic(), firstReParsed), is(true));
        assertThat(ControlPlaneAuthenticationHandlerFactory.sameKeyMaterial(first.getPublic(), second.getPublic()), is(false));
        assertThat(ControlPlaneAuthenticationHandlerFactory.sameKeyMaterial(first.getPublic(), PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE).getPublicKey()), is(false));
    }

    @Test
    public void shouldTrustDynamicallyCreatedCaWhenNoChainIsSet() throws IOException {
        Configuration configuration = mutualTlsServer()
            .dynamicallyCreateCertificateAuthorityCertificate(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath())
            .controlPlaneTLSMutualAuthenticationCAChain("");

        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(configuration);
        X509Certificate[] trusted = ControlPlaneAuthenticationHandlerFactory.controlPlaneClientCertificateTrust("", configuration, mockServerLogger);

        assertThat(trusted.length, is(1));
        assertThat(ControlPlaneAuthenticationHandlerFactory.containsBundledCertificateAuthority(trusted), is(false));
    }

    @Test
    public void shouldNotWarnWhenChainExcludesBundledCa() {
        CapturingLogger logger = new CapturingLogger();

        ControlPlaneAuthenticationHandlerFactory.controlPlaneClientCertificateTrust(CONTROL_PLANE_CA_CHAIN, withBundledCa(), logger);

        assertThat(logger.messages(Level.WARN), empty());
    }

    @Test
    public void shouldRefuseMutualTlsWithoutChainWhenMockServerCaIsTheBundledCa() {
        ControlPlaneMutualTlsConfigurationException exception = assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withBundledCa().controlPlaneTLSMutualAuthenticationCAChain("")));

        assertThat(exception.getMessage(), allOf(
            containsString("set controlPlaneTLSMutualAuthenticationCAChain"),
            containsString("configure MockServer's own CA with certificateAuthorityCertificate and certificateAuthorityPrivateKey")
        ));
    }

    @Test
    public void shouldRefuseMutualTlsWithoutChainWhenMockServerCaIsACopyOfTheBundledCa() throws IOException {
        File copy = temporaryFolder.newFile("copy-of-bundled-ca.pem");
        Files.write(copy.toPath(), readFileFromClassPathOrPath(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE).getBytes(StandardCharsets.UTF_8));

        assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withBundledCa().certificateAuthorityCertificate(copy.getAbsolutePath()).controlPlaneTLSMutualAuthenticationCAChain("")));
    }

    @Test
    public void shouldAcceptUsableControlPlaneMutualTlsSetUps() {
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withBundledCa().controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN));
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withBundledCa().controlPlaneTLSMutualAuthenticationRequired(false).controlPlaneTLSMutualAuthenticationCAChain(""));
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(withBundledCa().dynamicallyCreateCertificateAuthorityCertificate(true).controlPlaneTLSMutualAuthenticationCAChain(""));
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(mutualTlsServer()
            .certificateAuthorityCertificate(CONTROL_PLANE_CA_CHAIN)
            .certificateAuthorityPrivateKey(CONTROL_PLANE_CA_PRIVATE_KEY)
            .controlPlaneTLSMutualAuthenticationCAChain(""));
    }

    @Test
    public void shouldRefuseAnUpdateThatLeavesMutualTlsWithoutChainAndTheBundledCa() {
        Configuration withoutMutualTls = withBundledCa().controlPlaneTLSMutualAuthenticationRequired(false).controlPlaneTLSMutualAuthenticationCAChain("");
        Configuration withChain = withBundledCa().controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN);

        assertThrows(ControlPlaneMutualTlsConfigurationException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationRequired(true), withoutMutualTls));
        assertThrows(ControlPlaneMutualTlsConfigurationException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationCAChain(""), withChain));
        assertThrows(ControlPlaneMutualTlsConfigurationException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationCAChain("").setCertificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE),
            mutualTlsServer().certificateAuthorityCertificate(CONTROL_PLANE_CA_CHAIN).certificateAuthorityPrivateKey(CONTROL_PLANE_CA_PRIVATE_KEY).controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN)));
    }

    @Test
    public void shouldAcceptAnUpdateThatLeavesUsableMutualTls() {
        Configuration withoutMutualTls = withBundledCa().controlPlaneTLSMutualAuthenticationRequired(false).controlPlaneTLSMutualAuthenticationCAChain("");

        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationRequired(true).setControlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN), withoutMutualTls);
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setControlPlaneTLSMutualAuthenticationRequired(true).setDynamicallyCreateCertificateAuthorityCertificate(true), withoutMutualTls);
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneMutualTls(
            new ConfigurationDTO().setMaxExpectations(10), withoutMutualTls);
    }

    private static Configuration mutualTlsServer() {
        return configuration()
            .controlPlaneTLSMutualAuthenticationRequired(true)
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false)
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .proactivelyInitialiseTLS(false);
    }

    private static X509Certificate selfSignedCertificateAuthority(SubjectPublicKeyInfo publicKey, PrivateKey signingKey) throws Exception {
        X500Name name = new X500Name("CN=re-encoded bundled key " + System.nanoTime());
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()), TestCertificates.daysFromNow(-1), TestCertificates.daysFromNow(30), name, publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature));
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signingKey)));
    }

    private static Configuration withOwnCa() {
        return mutualTlsServer()
            .certificateAuthorityCertificate(CONTROL_PLANE_CA_CHAIN)
            .certificateAuthorityPrivateKey(CONTROL_PLANE_CA_PRIVATE_KEY);
    }

    private static String chainWithBundledCa() throws IOException {
        File chain = temporaryFolder.newFile();
        Files.write(chain.toPath(), (readFileFromClassPathOrPath(CONTROL_PLANE_CA_CHAIN) + "\n"
            + readFileFromClassPathOrPath(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)).getBytes(StandardCharsets.UTF_8));
        return chain.getAbsolutePath();
    }

    private static Configuration withBundledCa() {
        return mutualTlsServer()
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
    }

    /**
     * A certificate MockServer issues with its bundled CA, which permits client authentication.
     */
    private X509Certificate signedByBundledCa() throws IOException {
        KeyAndCertificateFactory factory = createKeyAndCertificateFactory(
            withBundledCa().directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath()),
            mockServerLogger
        );
        factory.buildAndSavePrivateKeyAndX509Certificate();
        X509Certificate certificate = factory.x509Certificate();
        assertThat(certificate.getIssuerX500Principal(), is(PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE).getSubjectX500Principal()));
        return certificate;
    }

    private HttpRequest requestPresenting(X509Certificate certificate) {
        return new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(request(), new X509Certificate[]{certificate});
    }

    private static class CapturingLogger extends MockServerLogger {
        private final List<LogEntry> logged = new CopyOnWriteArrayList<>();

        CapturingLogger() {
            super(configuration().logLevel("INFO"), ControlPlaneMutualTlsClientCertificateTrustTest.class);
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
