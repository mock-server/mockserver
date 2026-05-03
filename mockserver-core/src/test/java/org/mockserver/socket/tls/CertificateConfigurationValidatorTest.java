package org.mockserver.socket.tls;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;

public class CertificateConfigurationValidatorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldPassWithDefaultConfiguration() {
        Configuration config = configuration();
        new CertificateConfigurationValidator(config, mockServerLogger).validate();
    }

    @Test
    public void shouldFailWhenOnlyPrivateKeyPathSet() throws IOException {
        File keyFile = createTempPemFile("key.pem", getDummyPrivateKeyPem());
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Both 'privateKeyPath' and 'x509CertificatePath' must be configured together"));
            assertThat(e.getMessage(), containsString("'x509CertificatePath' is not set"));
        }
    }

    @Test
    public void shouldFailWhenOnlyX509CertificatePathSet() throws IOException {
        File certFile = createTempPemFile("cert.pem", getDummyCertPem());
        Configuration config = configuration();
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Both 'privateKeyPath' and 'x509CertificatePath' must be configured together"));
            assertThat(e.getMessage(), containsString("'privateKeyPath' is not set"));
        }
    }

    @Test
    public void shouldFailWithInvalidPrivateKeyFile() throws IOException {
        File keyFile = createTempPemFile("key.pem", "not a valid pem");
        File certFile = createTempPemFile("cert.pem", getDummyCertPem());
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("is not a valid PEM-encoded private key"));
        }
    }

    @Test
    public void shouldFailWithInvalidCertificateFile() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", "not a valid pem");
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("is not a valid PEM-encoded certificate"));
        }
    }

    @Test
    public void shouldPassWithMatchingKeyAndCert() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        new CertificateConfigurationValidator(config, mockServerLogger).validate();
    }

    @Test
    public void shouldFailWithMismatchedKeyAndCert() throws Exception {
        String[] keyAndCert1 = generateSelfSignedKeyAndCert();
        String[] keyAndCert2 = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert1[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert2[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("does not match the certificate"));
        }
    }

    @Test
    public void shouldFailWithInvalidCaCertificateFile() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", "not a valid pem");
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("is not a valid PEM-encoded X.509 certificate"));
        }
    }

    @Test
    public void shouldFailWhenCertNotSignedByCa() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        String[] caKeyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", caKeyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("was not signed by the CA certificate"));
        }
    }

    @Test
    public void shouldPassWhenCertIsSignedByCa() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        new CertificateConfigurationValidator(config, mockServerLogger).validate();
    }

    @Test
    public void shouldFailWithInvalidCaPrivateKeyFile() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", keyAndCert[1]);
        File caKeyFile = createTempPemFile("cakey.pem", "not a valid pem");
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());
        config.certificateAuthorityPrivateKey(caKeyFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("CA private key file"));
            assertThat(e.getMessage(), containsString("is not a valid PEM-encoded private key"));
        }
    }

    @Test
    public void shouldSkipCaCertFileValidationWhenPathIsDefault() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        File caCertFile = createTempPemFile("ca.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        new CertificateConfigurationValidator(config, mockServerLogger).validate();
    }

    @Test
    public void shouldFailWithExpiredCertificate() throws Exception {
        String[] keyAndCert = generateKeyAndCertWithDates(
            new Date(System.currentTimeMillis() - 86400000L * 365 * 2),
            new Date(System.currentTimeMillis() - 86400000L)
        );
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("expired on"));
            assertThat(e.getMessage(), containsString("Replace it with a valid certificate"));
        }
    }

    @Test
    public void shouldFailWithNotYetValidCertificate() throws Exception {
        String[] keyAndCert = generateKeyAndCertWithDates(
            new Date(System.currentTimeMillis() + 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365)
        );
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("is not yet valid until"));
            assertThat(e.getMessage(), containsString("Replace it with a valid certificate"));
        }
    }

    @Test
    public void shouldFailWhenCustomLeafUsedWithDefaultCa() throws Exception {
        String[] keyAndCert = generateSelfSignedKeyAndCert();
        File keyFile = createTempPemFile("key.pem", keyAndCert[0]);
        File certFile = createTempPemFile("cert.pem", keyAndCert[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());

        try {
            new CertificateConfigurationValidator(config, mockServerLogger).validate();
            fail("expected RuntimeException because custom leaf is not signed by default CA");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("was not signed by the CA certificate"));
        }
    }

    @Test
    public void shouldPassWithMultipleCertsInPemFile() throws Exception {
        String[] keyAndCert1 = generateSelfSignedKeyAndCert();
        String[] keyAndCert2 = generateSelfSignedKeyAndCert();
        String chainPem = keyAndCert1[1] + keyAndCert2[1];
        File keyFile = createTempPemFile("key.pem", keyAndCert1[0]);
        File certFile = createTempPemFile("cert.pem", chainPem);
        File caCertFile = createTempPemFile("ca.pem", keyAndCert1[1]);
        Configuration config = configuration();
        config.privateKeyPath(keyFile.getAbsolutePath());
        config.x509CertificatePath(certFile.getAbsolutePath());
        config.certificateAuthorityCertificate(caCertFile.getAbsolutePath());

        new CertificateConfigurationValidator(config, mockServerLogger).validate();
    }

    private File createTempPemFile(String name, String content) throws IOException {
        File file = tempFolder.newFile(name);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    private String getDummyPrivateKeyPem() {
        return "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEowIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF8PbnGy0AHB7MhgHcTz6sE2I2yPB\n" +
            "-----END RSA PRIVATE KEY-----";
    }

    private String getDummyCertPem() {
        return "-----BEGIN CERTIFICATE-----\n" +
            "MIICpDCCAYwCCQDMq2inYDfBQjANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDDAls\n" +
            "-----END CERTIFICATE-----";
    }

    private String[] generateSelfSignedKeyAndCert() throws Exception {
        return generateKeyAndCertWithDates(
            new Date(System.currentTimeMillis() - 86400000L),
            new Date(System.currentTimeMillis() + 86400000L * 365)
        );
    }

    private String[] generateKeyAndCertWithDates(Date notBefore, Date notAfter) throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        org.bouncycastle.asn1.x500.X500Name issuer = new org.bouncycastle.asn1.x500.X500Name("CN=Test");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic()
        );

        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());

        X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certBuilder.build(signer));

        String keyPem = pemEncode("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String certPem = pemEncode("CERTIFICATE", cert.getEncoded());

        return new String[]{keyPem, certPem};
    }

    private String pemEncode(String type, byte[] encoded) {
        java.util.Base64.Encoder encoder = java.util.Base64.getMimeEncoder(64, "\n".getBytes());
        return "-----BEGIN " + type + "-----\n" +
            encoder.encodeToString(encoded) +
            "\n-----END " + type + "-----\n";
    }
}
