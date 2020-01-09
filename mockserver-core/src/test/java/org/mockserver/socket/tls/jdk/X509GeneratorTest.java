package org.mockserver.socket.tls.jdk;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import sun.security.x509.X509CertImpl;

import java.math.BigInteger;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.ROOT_COMMON_NAME;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.VALIDITY;
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

        // and -  a key of 2048 bits
        int keySize = KEY_SIZE;

        // when -  a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // then -  algorithm is RSA
        assertEquals("RSA", x509CertImpl.getPublicKey().getAlgorithm());

        // and -  and key size is 2048
        assertEquals(keySize, ((RSAPublicKey) x509CertImpl.getPublicKey()).getModulus().bitLength());
    }

    @Test
    public void shouldCreateRootCertificateWithValidExpiry() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // when -  expiration is verified
        LocalDateTime instantWithinValidityRange = LocalDateTime.now().plusDays(30);
        x509CertImpl.checkValidity(Date.from(instantWithinValidityRange.atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    public void shouldCreateRootCertificateThatIsNotYetValid() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // when -  expiration is verified
        LocalDateTime instantBeforeIssueTime = LocalDateTime.now().minusMonths(1).minusHours(1);
        try {
            x509CertImpl.checkValidity(Date.from(instantBeforeIssueTime.atZone(ZoneId.systemDefault()).toInstant()));
            fail();
        } catch (CertificateNotYetValidException cynyve) {
            assertThat(cynyve.getMessage(), containsString("NotBefore"));
        }
    }

    @Test
    public void shouldCreateRootCertificateThatExpires() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  a key pair
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // when -  expiration is verified
        LocalDateTime instantAfterIssueTime = LocalDateTime.now().plusDays(VALIDITY).plusHours(1);
        try {
            x509CertImpl.checkValidity(Date.from(instantAfterIssueTime.atZone(ZoneId.systemDefault()).toInstant()));
            fail();
        } catch (CertificateExpiredException cynyve) {
            assertThat(cynyve.getMessage(), containsString("NotAfter"));
        }
    }

    @Test
    public void shouldCreateRootCertificateThatCanBePEMEncodedAndDecoded() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // when -  a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);

        // then -  validate pem decoding/encoding of the private key
        assertEquals(keyPair.getPrivateKey(), x509Generator.privateKeyToPem(x509Generator.privateKeyFromPem(keyPair.getPrivateKey())));

        // and -  validate pem decoding/encoding of the cert
        assertEquals(keyPair.getCert(), x509Generator.certToPem(x509Generator.certFromPem(keyPair.getCert())));
    }

    @Test
    public void shouldCreateClientCertificateWithTheProvidedSubjectAlternativesNames() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  A certificate siging request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        List<String> providedSubjectAlternativeNames = Arrays.asList("bob.com", "localhost.com", "127.0.0.1");
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and -  and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when -  A certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // then -  The correct number of SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509CertImpl.getSubjectAlternativeNames();
        assertEquals(3, subjectAlternativeNames.size());

        // and -  The correct values are contained in the correct order
        assertArrayEquals(subjectAlternativeNames
            .stream()
            .map(subjectAlternativeName -> subjectAlternativeName.get(1)).toArray(), providedSubjectAlternativeNames.toArray());
    }

    @Test
    public void shouldCreateClientCertificateWithEmptySANs() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  A certificate signing request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        csr.setSubjectAlternativeNames(emptyList());

        // and -  and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when -  A certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // then -  The no SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509CertImpl.getSubjectAlternativeNames();
        assertNull(subjectAlternativeNames);
    }

    @Test
    public void shouldCreateClientCertificateAndIgnoreEmailAddressForSANs() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  A certificate siging request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        List<String> providedSubjectAlternativeNames = Arrays.asList("bob@bob.com", "localhost.com", "127.0.0.1");
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and -  and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when -  A certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        // then -  The correct number of SANs should be present
        Collection<List<?>> subjectAlternativeNames = x509CertImpl.getSubjectAlternativeNames();
        assertEquals(2, subjectAlternativeNames.size());

        // and -  The correct values are contained in the correct order
        assertArrayEquals(new String[]{"localhost.com", "127.0.0.1"}, subjectAlternativeNames
            .stream()
            .map(subjectAlternativeName -> subjectAlternativeName.get(1)).toArray());

    }

    @Test
    public void shouldCreateRootCertificateWithCorrectCertExtensions() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  a certificate signing request
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);

        // and -  a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when -  a x509 certificate has been successfully generated
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(pemRootKeyPair.getCert()));
        boolean[] keyUsage = x509CertImpl.getKeyUsage();

        // then -  the extensions are correctly set
        assertArrayEquals(new boolean[]{false, false, false, false, false, true, false, false, false}, keyUsage);
        x509CertImpl.getBasicConstraintsExtension().get(IS_CA);
    }

    @Test
    public void shouldCreateRootCertificateWithPositiveSerialNumber() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // when -  a key pair is generated
        X509AndPrivateKey keyPair = x509Generator.generateRootKeyPair(csr);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        assertTrue("The ca cert serial number is non-negative", x509CertImpl.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shallCreateClientCertificateWithPositiveSerialNumber() throws Exception {
        // given
        X509Generator x509Generator = new X509Generator(new MockServerLogger());

        // and -  A certificate siging request with SANs
        CertificateSigningRequest csr = new CertificateSigningRequest()
            .setCommonName(ROOT_COMMON_NAME)
            .setKeyPairSize(KEY_SIZE);
        List<String> providedSubjectAlternativeNames = Arrays.asList("bob.com", "localhost.com", "127.0.0.1");
        csr.setSubjectAlternativeNames(providedSubjectAlternativeNames);

        // and -  and a root keypair
        X509AndPrivateKey pemRootKeyPair = x509Generator.generateRootKeyPair(csr);

        // when -  A certificate has been successfully generated
        X509AndPrivateKey keyPair = x509Generator.generateSignedEphemeralCertificate(csr, pemRootKeyPair);
        X509CertImpl x509CertImpl = new X509CertImpl(x509Generator.certFromPem(keyPair.getCert()));

        assertTrue("The client cert serial number is non-negative", x509CertImpl.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

}