package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.junit.MockServerRule;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class MockServerRuleIntegrationTest extends AbstractMockServerNettyIntegrationTest {

    // used fixed port for rule for all tests to ensure MockServer has been shutdown fully between each test
    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, SERVER_HTTP_PORT);

    @BeforeClass
    public static void startServer() {
        // start echo servers
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT, false);
    }

    @AfterClass
    public static void stopServer() {
        // stop echo server
        echoServer.stop();
    }

    @Before
    @Override
    public void resetServer() {
        // do not reset as MockServerRule should handle that
    }

    @Override
    public int getMockServerPort() {
        return mockServerRule.getPort();
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServerRule.getPort();
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }
}
