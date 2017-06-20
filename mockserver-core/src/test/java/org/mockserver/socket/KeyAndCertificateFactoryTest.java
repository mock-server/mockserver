package org.mockserver.socket;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertTrue;
import static org.mockserver.socket.KeyAndCertificateFactory.keyAndCertificateFactory;

import java.security.cert.X509Certificate;
import java.security.KeyPair;
import java.math.BigInteger;

import java.lang.Exception;

/**
 * @author jnormington
 */
public class KeyAndCertificateFactoryTest {

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber()  throws Exception {
        keyAndCertificateFactory().buildAndSaveCertificates();

        assertTrue("The ca cert serial number is non-negative",
                keyAndCertificateFactory().mockServerCertificateAuthorityX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber()  throws Exception {
        keyAndCertificateFactory().buildAndSaveCertificates();

        assertTrue("The client cert serial number is non-negative",
                keyAndCertificateFactory().mockServerX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }
}
