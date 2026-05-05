package org.mockserver;

import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;

import javax.naming.Context;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginStopTest {

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachMockServer() {
        new MockServerClient("127.0.0.1", 1080).reset();
    }

}
