package org.mockserver.integration.mocking;

import org.junit.*;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.junit.MockServerRule;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class JUnitClassRuleIntegrationTest extends AbstractBasicMockingIntegrationTest {

    // used fixed port for rule for all tests to ensure MockServer has been shutdown fully between each test
    private static final int MOCK_SERVER_PORT = PortFactory.findFreePort();

    @ClassRule
    public static MockServerRule mockServerRule = new MockServerRule(JUnitClassRuleIntegrationTest.class, MOCK_SERVER_PORT);

    @Before
    @Override
    public void resetServer() {
        mockServerClient.reset();
    }

    @Override
    public int getServerPort() {
        return mockServerRule.getPort();
    }

    @Override
    public int getEchoServerPort() {
        return insecureEchoServer.getPort();
    }
}
