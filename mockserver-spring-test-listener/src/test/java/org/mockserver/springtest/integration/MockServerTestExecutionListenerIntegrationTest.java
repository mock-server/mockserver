package org.mockserver.springtest.integration;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.springtest.MockServerPort;
import org.mockserver.springtest.MockServerTest;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
@RunWith(SpringRunner.class)
@MockServerTest
public class MockServerTestExecutionListenerIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    @MockServerPort
    private Integer mockServerPort;

    @Before
    @Override
    public void resetServer() {
        // do not reset as @MockServerTest should handle that
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
        return mockServerPort;
    }

}
