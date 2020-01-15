package org.mockserver.junit.integration;

import org.junit.*;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;
import org.mockserver.junit.MockServerRule;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class JUnitRuleIntegrationTest extends AbstractBasicMockingIntegrationTest {

    // used fixed port for rule for all tests to ensure MockServer has been fully shutdown between each test
    private static final int MOCK_SERVER_PORT = PortFactory.findFreePort();

    @Rule
    public final MockServerRule mockServerRule = new MockServerRule(this, MOCK_SERVER_PORT);

    @Before
    @Override
    public void resetServer() {
        // do not reset as MockServerRule should handle that
    }

    @Override
    public int getServerPort() {
        return mockServerRule.getPort();
    }

}
