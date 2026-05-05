package org.mockserver.netty.integration.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

import static org.mockserver.configuration.ConfigurationProperties.certificateAuthorityCertificate;
import static org.mockserver.configuration.ConfigurationProperties.certificateAuthorityPrivateKey;
import static org.mockserver.configuration.ConfigurationProperties.privateKeyPath;
import static org.mockserver.configuration.ConfigurationProperties.x509CertificatePath;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author pascal-hofmann
 */
public class CustomPrivateKeyAndCertificateWithECKeysMockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static int mockServerPort;
    private static String originalCertificateAuthorityCertificate;
    private static String originalCertificateAuthorityPrivateKey;
    private static String originalPrivateKeyPath;
    private static String originalX509CertificatePath;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        originalCertificateAuthorityPrivateKey = certificateAuthorityPrivateKey();
        originalPrivateKeyPath = privateKeyPath();
        originalX509CertificatePath = x509CertificatePath();

        // set new values
        certificateAuthorityCertificate("org/mockserver/netty/integration/tls/ec/ca.pem");
        certificateAuthorityPrivateKey("org/mockserver/netty/integration/tls/ec/ca-key-pkcs8.pem");
        privateKeyPath("org/mockserver/netty/integration/tls/ec/leaf-key-pkcs8.pem");
        x509CertificatePath("org/mockserver/netty/integration/tls/ec/leaf-cert.pem");

        mockServerClient = startClientAndServer();
        mockServerPort = mockServerClient.getPort();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        certificateAuthorityPrivateKey(originalCertificateAuthorityPrivateKey);
        privateKeyPath(originalPrivateKeyPath);
        x509CertificatePath(originalX509CertificatePath);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

}
