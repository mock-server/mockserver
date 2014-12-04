package org.mockserver.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private MockServer mockServer;

    public ClientAndServer() {
        this(PortFactory.findFreePort());
    }

    public ClientAndServer(Integer port) {
        super("localhost", port);
        mockServer = new MockServer(port);
    }

    public static ClientAndServer startClientAndServer(Integer port) {
        return new ClientAndServer(port);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

}
