package org.mockserver.springtest.integration;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockserver.springtest.MockServerPort;
import org.mockserver.springtest.MockServerTest;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author jamesdbloom
 */
@RunWith(SpringRunner.class)
@MockServerTest
public class MockServerTestExecutionListenerIntegrationTest extends AbstractBasicMockingIntegrationTest {

    @MockServerPort
    private Integer mockServerPort;

    @Before
    @Override
    public void resetServer() {
        // do not reset as @MockServerTest should handle that
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

}
