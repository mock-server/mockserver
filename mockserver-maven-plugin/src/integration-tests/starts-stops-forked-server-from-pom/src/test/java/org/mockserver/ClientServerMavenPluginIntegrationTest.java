package org.mockserver;

import org.junit.Before;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int port = 8080;

    @Before
    public void clearServer() {
        mockServerClient.clear(new HttpRequest());
    }

    @Override
    public int getPort() {
        return port;
    }

}
