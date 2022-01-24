package org.mockserver.socket.tls.jdk;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;

import java.math.BigInteger;

import static junit.framework.TestCase.assertTrue;

/**
 * @author jnormington
 */
public class JDKKeyAndCertificateFactoryTest {

    private final KeyAndCertificateFactory keyAndCertificateFactory = new JDKKeyAndCertificateFactory(new MockServerLogger());

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();

        assertTrue("The ca cert serial number is non-negative", keyAndCertificateFactory.certificateAuthorityX509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber() {
        keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();

        assertTrue("The client cert serial number is non-negative", keyAndCertificateFactory.x509Certificate().getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }
}
