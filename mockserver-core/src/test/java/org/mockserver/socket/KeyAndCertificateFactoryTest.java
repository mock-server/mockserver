package org.mockserver.socket;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertTrue;

import java.security.cert.X509Certificate;
import java.security.KeyPair;
import java.math.BigInteger;

import java.lang.Exception;

/**
 * @author jnormington
 */
public class KeyAndCertificateFactoryTest {

    KeyAndCertificateFactory keyAndCertificateFactory;
    KeyPair caKeyPair;

    @Before
    public void setUp() throws Exception {
        this.keyAndCertificateFactory = new KeyAndCertificateFactory();
        this.caKeyPair = this.keyAndCertificateFactory.generateKeyPair(1024);
    }

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber()  throws Exception {
        X509Certificate newCaCert = this.keyAndCertificateFactory.createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate());
        assertTrue("The cacert serial number is non-negative",
            newCaCert.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber()  throws Exception {
        X509Certificate caCert = this.keyAndCertificateFactory.createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate());
        KeyPair clientKeyPair = this.keyAndCertificateFactory.generateKeyPair(1024);

        X509Certificate clientCert = this.keyAndCertificateFactory.createMockServerCert(
            clientKeyPair.getPublic(),
            caCert,
            this.caKeyPair.getPrivate(),
            this.caKeyPair.getPublic(),
            "example.com",
            new String[]{ "www.example.com"},
            null
        );

        assertTrue("The client cert serial number is non-negative",
            clientCert.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }
}
