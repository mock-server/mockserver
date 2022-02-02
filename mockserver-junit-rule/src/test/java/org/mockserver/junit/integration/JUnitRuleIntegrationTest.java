package org.mockserver.junit.integration;

import org.junit.*;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;
import org.mockserver.junit.MockServerRule;
import org.mockserver.socket.PortFactory;

import static org.slf4j.event.Level.WARN;

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
        try {
            if (insecureEchoServer != null) {
                insecureEchoServer.clear();
            }
            if (secureEchoServer != null) {
                secureEchoServer.clear();
            }
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(WARN)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while resetting - " + throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    @Override
    public int getServerPort() {
        return mockServerRule.getPort();
    }

}
