package org.mockserver.socket.tls;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.JDKKeyAndCertificateFactory;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

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
