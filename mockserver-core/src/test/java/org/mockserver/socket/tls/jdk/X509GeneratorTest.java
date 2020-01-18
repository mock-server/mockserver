package org.mockserver.socket.tls.jdk;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import sun.security.x509.X509CertImpl;

import java.math.BigInteger;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.DEFAULT_VALIDITY;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.ROOT_COMMON_NAME;
import static org.mockserver.socket.tls.jdk.X509Generator.x509FromPEM;
import static sun.security.x509.BasicConstraintsExtension.IS_CA;

public class X509GeneratorTest {

    private Integer KEY_SIZE = 512;
    private CertificateSigningRequest csr = new CertificateSigningRequest()
        .setCommonName(ROOT_COMMON_NAME)
        .setKeyPairSize(KEY_SIZE);

    @Test
    public void shouldCreateRootCertificateWithCorrectAlgorithmAndKeySize() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a key of 2048 bits
        int keySize = KEY_SIZE;

        // when - a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // then - algorithm is RSA
        assertEquals("RSA", x509Certificate.getPublicKey().getAlgorithm());

        // and - and key size is 2048
        assertEquals(keySize, ((RSAPublicKey) x509Certificate.getPublicKey()).getModulus().bitLength());
    }

    @Test
    public void shouldCreateRootCertificateWithValidExpiry() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // when - expiration is verified
        LocalDateTime instantWithinValidityRange = LocalDateTime.now().plusDays(30);
        x509Certificate.checkValidity(Date.from(instantWithinValidityRange.atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    public void shouldCreateRootCertificateThatIsNotYetValid() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // when - expiration is verified
        LocalDateTime instantBeforeIssueTime = LocalDateTime.now().minusMonths(1).minusHours(1);
        try {
            x509Certificate.checkValidity(Date.from(instantBeforeIssueTime.atZone(ZoneId.systemDefault()).toInstant()));
            fail("expected exception to be thrown");
        } catch (CertificateNotYetValidException cynyve) {
            assertThat(cynyve.getMessage(), containsString("NotBefore"));
        }
    }

    @Test
    public void shouldCreateRootCertificateThatExpires() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // when - expiration is verified
        LocalDateTime instantAfterIssueTime = LocalDateTime.now().plusDays(DEFAULT_VALIDITY).plusHours(1);
        try {
            x509Certificate.checkValidity(Date.from(instantAfterIssueTime.atZone(ZoneId.systemDefault()).toInstant()));
            fail("expected exception to be thrown");
        } catch (CertificateExpiredException cynyve) {
            assertThat(cynyve.getMessage(), containsString("NotAfter"));
        }
    }

    @Test
    public void shouldCreateRootCertificateThatCanBePEMEncodedAndDecoded() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // when - a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);

        // then - validate pem decoding/encoding of the private key
        assertEquals(keyPair.getPrivateKey(), x509Generator.privateKeyToPEM(x509Generator.privateKeyBytesFromPEM(keyPair.getPrivateKey())));

        // and - validate pem decoding/encoding of the cert
        assertEquals(keyPair.getCert(), x509Generator.certToPEM(certFromPem(keyPair.getCert())));
    }

    @Test
    public void shouldCreateClientCertificateWithTheProvidedSubjectAlternativesNames() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a certificate siging request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        String[] providedSubjectAlternativeNames = new String[]{"bob.com", "localhost.com", "127.0.0.1"};
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and - and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when - a certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair.getPrivateKey());
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // then - the correct number of SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
        assertEquals(3, subjectAlternativeNames.size());

        // and - the correct values are contained in the correct order
        assertArrayEquals(subjectAlternativeNames
            .stream()
            .map(subjectAlternativeName -> subjectAlternativeName.get(1)).toArray(), providedSubjectAlternativeNames);
    }

    @Test
    public void shouldCreateClientCertificateWithEmptySANs() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a certificate signing request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        csr.setSubjectAlternativeNames(new String[0]);

        // and - and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when - a certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair.getPrivateKey());
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // then - the no SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
        assertNull(subjectAlternativeNames);
    }

    @Test
    public void shouldCreateClientCertificateAndIgnoreEmailAddressForSANs() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a certificate siging request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        String[] providedSubjectAlternativeNames = new String[]{"bob@bob.com", "localhost.com", "127.0.0.1"};
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and - and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when - a certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair.getPrivateKey());
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        // then - the correct number of SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
        assertEquals(2, subjectAlternativeNames.size());

        // and - the correct values are contained in the correct order
        assertArrayEquals(new String[]{"localhost.com", "127.0.0.1"}, subjectAlternativeNames
            .stream()
            .map(subjectAlternativeName -> subjectAlternativeName.get(1)).toArray());

    }

    @Test
    public void shouldCreateRootCertificateWithCorrectCertExtensions() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a certificate signing request
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);

        // and - a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when - a x509 certificate has been successfully generated
        X509Certificate x509Certificate = x509FromPEM(pemRootKeyPair.getCert());
        boolean[] keyUsage = x509Certificate.getKeyUsage();

        // then - the extensions are correctly set
        assertArrayEquals(new boolean[]{false, false, false, false, false, true, false, false, false}, keyUsage);
        if (x509Certificate instanceof X509CertImpl) {
            ((X509CertImpl) x509Certificate).getBasicConstraintsExtension().get(IS_CA);
        }
    }

    @Test
    public void shouldCreateRootCertificateWithPositiveSerialNumber() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // when - a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        assertTrue("The ca cert serial number is non-negative", x509Certificate.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shallCreateClientCertificateWithPositiveSerialNumber() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and - a certificate signing request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        String[] providedSubjectAlternativeNames = new String[]{"bob.com", "localhost.com", "127.0.0.1"};
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and - and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when - a certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair.getPrivateKey());
        X509Certificate x509Certificate = x509FromPEM(keyPair.getCert());

        assertTrue("The client cert serial number is non-negative", x509Certificate.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    byte[] certFromPem(final String cert) {
        return Base64
            .getMimeDecoder()
            .decode(
                cert
                    .replaceFirst("-----BEGIN CERTIFICATE-----", EMPTY)
                    .replaceFirst("-----END CERTIFICATE-----", EMPTY)
            );
    }

}