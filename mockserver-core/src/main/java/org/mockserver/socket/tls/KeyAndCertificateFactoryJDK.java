package org.mockserver.socket.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author jamesdbloom, ganskef
 */
public class KeyAndCertificateFactoryJDK implements KeyAndCertificateFactory {

    @Override
    public boolean certificateCreated() {
        throw new RuntimeException("method not yet implemented");
    }

    @Override
    public void buildAndSaveCertificates() {
        throw new RuntimeException("method not yet implemented");
    }

    @Override
    public PrivateKey privateKey() {
        throw new RuntimeException("method not yet implemented");
    }

    @Override
    public X509Certificate x509Certificate() {
        throw new RuntimeException("method not yet implemented");
    }

    @Override
    public X509Certificate certificateAuthorityX509Certificate() {
        throw new RuntimeException("method not yet implemented");
    }
}
