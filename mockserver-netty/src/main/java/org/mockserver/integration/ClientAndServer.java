package org.mockserver.integration;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient implements TestRule {

    private MockServer mockServer = new MockServer();

    public ClientAndServer() {
        super("localhost", PortFactory.findFreePort());
        mockServer.start(super.port, null);
    }

    public ClientAndServer(Integer port) {
        super("localhost", port);
        mockServer.start(port, null);
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

    @Override
    public Statement apply(Statement base, Description description) {
        throw new UnsupportedOperationException("method not implemented yet");
    }
}
