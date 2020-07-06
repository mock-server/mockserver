package org.mockserver.netty.integration.mock.tls.inbound.bouncycastle;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.integration.mock.tls.inbound.AbstractClientAuthenticationMockingIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.io.File;
import java.util.UUID;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationDynamicCAMockingIntegrationTest extends AbstractClientAuthenticationMockingIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void startServer() throws Exception {
        // temporary directory
        File temporaryDirectory = new File(File.createTempFile("random", "temp").getParent() + UUID.randomUUID().toString());
        temporaryDirectory.mkdirs();

        useBouncyCastleForKeyAndCertificateGeneration(true);
        tlsMutualAuthenticationRequired(true);
        dynamicallyCreateCertificateAuthorityCertificate(true);
        directoryToSaveDynamicSSLCertificate(temporaryDirectory.getAbsolutePath());
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        useBouncyCastleForKeyAndCertificateGeneration(false);
        tlsMutualAuthenticationRequired(false);
        dynamicallyCreateCertificateAuthorityCertificate(false);
        directoryToSaveDynamicSSLCertificate("");
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

}
