package org.mockserver.netty.integration.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationMockingIntegrationTest extends AbstractClientAuthenticationMockingIntegrationTest {

    private static boolean originalTLSMutualAuthenticationRequired;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalTLSMutualAuthenticationRequired = tlsMutualAuthenticationRequired();

        tlsMutualAuthenticationRequired(true);

        mockServerClient = ClientAndServer.startClientAndServer().withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        tlsMutualAuthenticationRequired(originalTLSMutualAuthenticationRequired);
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

}
