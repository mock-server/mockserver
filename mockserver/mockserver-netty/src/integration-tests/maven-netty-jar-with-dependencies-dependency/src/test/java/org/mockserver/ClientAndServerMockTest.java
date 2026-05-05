package org.mockserver;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientAndServerMockTest extends AbstractBasicMockingIntegrationTest {

    protected boolean supportsHTTP2() {
        // TODO(jamesdbloom) support copying native content into the no-dependencies jar
        return SslProvider.isAlpnSupported(SslContext.defaultServerProvider()) || SslProvider.isAlpnSupported(SslProvider.JDK) || SslProvider.isAlpnSupported(SslProvider.OPENSSL);
    }

    @BeforeClass
    public static void createClient() {
        mockServerClient = ClientAndServer.startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

}
