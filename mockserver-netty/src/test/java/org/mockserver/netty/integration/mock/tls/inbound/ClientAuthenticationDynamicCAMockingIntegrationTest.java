package org.mockserver.netty.integration.mock.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.socket.PortFactory;
import org.mockserver.uuid.UUIDService;

import java.io.File;

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
        tlsMutualAuthenticationRequired(true);
        dynamicallyCreateCertificateAuthorityCertificate(true);
        File temporaryDirectory = new File(File.createTempFile("random", "temp").getParent() + UUIDService.getUUID());
        temporaryDirectory.mkdirs();
        directoryToSaveDynamicSSLCertificate(temporaryDirectory.getAbsolutePath());
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        tlsMutualAuthenticationRequired(false);
        dynamicallyCreateCertificateAuthorityCertificate(false);
        directoryToSaveDynamicSSLCertificate("");
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

}
