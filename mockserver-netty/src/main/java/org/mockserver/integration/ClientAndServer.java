package org.mockserver.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServer mockServer;

    public ClientAndServer() {
        this(0);
    }

    public ClientAndServer(Integer... port) {
        this(new MockServer(port));
    }

    protected ClientAndServer(MockServer server) {
        super("localhost", server.getPort());
        this.mockServer = server;
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return new ClientAndServer(port);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

    public Integer getPort() {
        return mockServer.getPort();
    }

    public List<Integer> getPorts() {
        return mockServer.getPorts();
    }

}