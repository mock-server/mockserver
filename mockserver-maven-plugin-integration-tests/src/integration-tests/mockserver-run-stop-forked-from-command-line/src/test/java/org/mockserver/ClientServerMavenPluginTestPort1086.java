package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.unification.PortUnificationEchoServer;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginTestPort1086 extends AbstractClientServerIntegrationTest {

    private final static int ECHO_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTP_PORT = 1086;
    private static PortUnificationEchoServer echoServer;

    @BeforeClass
    public static void createClient() throws Exception {
        echoServer = new PortUnificationEchoServer(ECHO_SERVER_HTTP_PORT);
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        echoServer.stop();
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @Override
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerPort() {
        return ECHO_SERVER_HTTP_PORT;
    }

}
