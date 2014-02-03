package org.mockserver.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.NettyMockServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private NettyMockServer mockServer;

    public ClientAndServer(Integer port) {
        super("localhost", port);
        mockServer = new NettyMockServer().start(port, null);
    }

    public ClientAndServer(Integer port, Integer securePort) {
        super("localhost", port);
        mockServer = new NettyMockServer().start(port, securePort);
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
