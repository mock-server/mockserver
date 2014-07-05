package org.mockserver.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private MockServer mockServer = new MockServer();

    public ClientAndServer() {
        this(PortFactory.findFreePort());
    }

    public ClientAndServer(Integer port) {
        this(port, null);
    }

    public ClientAndServer(Integer port, Integer securePort) {
        super("localhost", port);
        mockServer.start(port, securePort);
    }

    public static ClientAndServer startClientAndServer(Integer port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(Integer port, Integer securePort) {
        return new ClientAndServer(port, securePort);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

}
