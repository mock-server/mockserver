package org.mockserver.netty.integration.mock.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.socket.PortFactory;

import static org.mockserver.configuration.ConfigurationProperties.tlsMutualAuthenticationRequired;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationMockingIntegrationTest extends AbstractClientAuthenticationMockingIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() {
        tlsMutualAuthenticationRequired(true);
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        tlsMutualAuthenticationRequired(false);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

}
