package org.mockserver.socket.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author jamesdbloom
 */
public interface KeyAndCertificateFactory {

    String KEY_GENERATION_ALGORITHM = "RSA";
    String SIGNING_ALGORITHM = "SHA256withRSA";

    @SuppressWarnings("unused")
    void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();

    void buildAndSavePrivateKeyAndX509Certificate();

    boolean certificateNotYetCreated();

    PrivateKey privateKey();

    X509Certificate x509Certificate();

    X509Certificate certificateAuthorityX509Certificate();

}
