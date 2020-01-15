package org.mockserver.socket.tls.jdk;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.mockserver.socket.tls.jdk.X509Generator.*;

/**
 * @author jamesdbloom, ganskef
 */
public class JDKKeyAndCertificateFactory implements KeyAndCertificateFactory {

    private final MockServerLogger mockServerLogger;

    private String mockServerCertificatePEMFile;
    private String mockServerPrivateKeyPEMFile;

    public JDKKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    public void buildAndSaveCertificates() {
        throw new RuntimeException("method not yet implemented");
    }

    public PrivateKey privateKey() {
        return loadPrivateKeyFromPEMFile(mockServerPrivateKeyPEMFile);
    }

    public X509Certificate x509Certificate() {
        return loadX509FromPEMFile(mockServerCertificatePEMFile);
    }

    public boolean certificateCreated() {
        return validX509PEMFileExists(mockServerCertificatePEMFile);
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        return loadX509FromPEMFile(ConfigurationProperties.certificateAuthorityCertificate());
    }
}
