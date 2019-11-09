package org.mockserver.socket.tls;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.math.BigInteger;

import static org.junit.Assert.assertTrue;

/**
 * @author jnormington
 */
public class KeyAndCertificateFactoryTest {

    private final KeyAndCertificateFactory keyAndCertificateFactory = new KeyAndCertificateFactory(new MockServerLogger());

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSaveCertificates();

        assertTrue("The ca cert serial number is non-negative",
            keyAndCertificateFactory.mockServerCertificateAuthorityX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSaveCertificates();

        assertTrue("The client cert serial number is non-negative",
            keyAndCertificateFactory.mockServerX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }
}
