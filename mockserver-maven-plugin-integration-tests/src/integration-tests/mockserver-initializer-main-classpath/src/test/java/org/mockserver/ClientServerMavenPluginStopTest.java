package org.mockserver;

import org.junit.Test;
import org.mockserver.client.server.MockServerClient;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginStopTest {

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachMockServer() {
        new MockServerClient("127.0.0.1", 8080).reset();
    }

}
