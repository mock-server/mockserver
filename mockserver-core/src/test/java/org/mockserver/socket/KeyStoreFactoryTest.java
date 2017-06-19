package org.mockserver.socket;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.assertTrue;

import java.security.cert.X509Certificate;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.math.BigInteger;

import java.lang.Exception;

/**
 * @author jnormington
 */
public class KeyStoreFactoryTest {

    KeyStoreFactory keyStoreFactory;
    KeyPair caKeyPair;

    @Before
    public void setUp() throws Exception {
        this.keyStoreFactory = new KeyStoreFactory();
        this.caKeyPair = this.keyStoreFactory.generateKeyPair(1024);
    }

    @Test
    public void shouldCreateCACertWithPositiveSerialNumber()  throws Exception {
        X509Certificate newCaCert = this.keyStoreFactory.createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate());
        assertTrue("The cacert serial number is non-negative",
            newCaCert.getSerialNumber().compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void shouldCreateClientCertWithPositiveSerialNumber()  throws Exception {
        X509Certificate caCert = this.keyStoreFactory.createCACert(caKeyPair.getPublic(), caKeyPair.getPrivate());
        KeyPair clientKeyPair = this.keyStoreFactory.generateKeyPair(1024);

        X509Certificate clientCert = this.keyStoreFactory.createMockServerCert(
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
