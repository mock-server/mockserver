package org.mockserver.socket.tls;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.math.BigInteger;

import static org.junit.Assert.assertTrue;

/**
 * @author jnormington
 */
public class KeyAndCertificateFactoryBCTest {

    private final BCKeyAndCertificateFactory keyAndCertificateFactory = new BCKeyAndCertificateFactory(new MockServerLogger());

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSaveCertificates();

        assertTrue("The ca cert serial number is non-negative",
            keyAndCertificateFactory.certificateAuthorityX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSaveCertificates();

        assertTrue("The client cert serial number is non-negative",
            keyAndCertificateFactory.x509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }
}
