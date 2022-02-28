package org.mockserver.netty.integration.mock.tls.inbound.bouncycastle;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.integration.mock.tls.inbound.AbstractClientAuthenticationMockingIntegrationTest;
import org.mockserver.socket.PortFactory;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.configuration.ConfigurationProperties.certificateAuthorityPrivateKey;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationMockingIntegrationTest extends AbstractClientAuthenticationMockingIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static boolean originalUseBouncyCastleForKeyAndCertificateGeneration;
    private static boolean originalTLSMutualAuthenticationRequired;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalUseBouncyCastleForKeyAndCertificateGeneration = useBouncyCastleForKeyAndCertificateGeneration();
        originalTLSMutualAuthenticationRequired = tlsMutualAuthenticationRequired();

        useBouncyCastleForKeyAndCertificateGeneration(true);
        tlsMutualAuthenticationRequired(true);

        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        useBouncyCastleForKeyAndCertificateGeneration(originalUseBouncyCastleForKeyAndCertificateGeneration);
        tlsMutualAuthenticationRequired(originalTLSMutualAuthenticationRequired);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

}
