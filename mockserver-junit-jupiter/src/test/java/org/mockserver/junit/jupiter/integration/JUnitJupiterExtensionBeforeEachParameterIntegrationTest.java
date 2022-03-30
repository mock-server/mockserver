package org.mockserver.junit.jupiter.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.test.TestLoggerExtension;

/**
 * @author jamesdbloom
 */
@ExtendWith({
    MockServerExtension.class,
    TestLoggerExtension.class,
})
class JUnitJupiterExtensionBeforeEachParameterIntegrationTest extends AbstractBasicMockingIntegrationTest {

    @BeforeEach
    public void injectClient(ClientAndServer client) {
        mockServerClient = client;
        mockServerClient.reset();
    }

    @Override
    @BeforeEach
    public void resetServer() {
        // do nothing as client may not yet be injected
    }

    @Override
    public int getServerPort() {
        return mockServerClient.remoteAddress().getPort();
    }

}