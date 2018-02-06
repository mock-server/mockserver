package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.junit.MockServerRule;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class JUnitRuleMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    // used fixed port for rule for all tests to ensure MockServer has been shutdown fully between each test
    private static final int MOCK_SERVER_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, MOCK_SERVER_PORT);

    @BeforeClass
    public static void startServer() {
        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        echoServer.stop();
    }

    @Before
    @Override
    public void resetServer() {
        // do not reset as MockServerRule should handle that
    }

    @Override
    public int getServerPort() {
        return mockServerRule.getPort();
    }

    @Override
    public int getEchoServerPort() {
        return echoServer.getPort();
    }
}
