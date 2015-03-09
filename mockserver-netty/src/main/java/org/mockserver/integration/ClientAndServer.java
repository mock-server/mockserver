package org.mockserver.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServer mockServer;

    public ClientAndServer() {
        this(0);
    }

    public ClientAndServer(Integer port) {
        this(new MockServer(port));
    }

    // have to use this extra constructor to grab the port from the already-started server since since java won't allow us to assign
    // this.mockServer before the call to super(), which itself requires the port assigned by the call to new MockServer(port).
    protected ClientAndServer(MockServer server) {
        super("localhost", server.getPort());
        this.mockServer = server;
    }

    public static ClientAndServer startClientAndServer(Integer port) {
        return new ClientAndServer(port);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

    public int getPort() {
        return mockServer.getPort();
    }

}
