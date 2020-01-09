package org.mockserver.socket.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface KeyAndCertificateFactory {

    void buildAndSaveCertificates();

    boolean certificateCreated();

    PrivateKey privateKey();

    X509Certificate x509Certificate();

    X509Certificate certificateAuthorityX509Certificate();

}
