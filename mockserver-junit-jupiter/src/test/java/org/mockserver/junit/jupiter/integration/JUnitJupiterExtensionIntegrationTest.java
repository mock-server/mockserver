package org.mockserver.junit.jupiter.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.test.TestLoggerExtension;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

/**
 * @author jamesdbloom
 */
@ExtendWith({
    MockServerExtension.class,
    TestLoggerExtension.class,
})
class JUnitJupiterExtensionIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    public JUnitJupiterExtensionIntegrationTest(ClientAndServer client) {
        mockServerClient = client;
    }

    @Override
    @BeforeEach
    public void resetServer() {
        mockServerClient.reset();
    }

    @Override
    public int getServerPort() {
        return mockServerClient.remoteAddress().getPort();
    }
}