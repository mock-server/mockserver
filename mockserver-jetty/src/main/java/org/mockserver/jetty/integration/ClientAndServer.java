package org.mockserver.jetty.integration;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.jetty.server.MockServerRunner;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServerRunner mockServerRunner;

    public ClientAndServer(Integer port) {
        super("localhost", port);
        mockServerRunner = new MockServerRunner().start(port, null);
    }

    public ClientAndServer(Integer port, Integer securePort) {
        super("localhost", port);
        mockServerRunner = new MockServerRunner().start(port, securePort);
    }

    public static ClientAndServer startClientAndServer(Integer port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(Integer port, Integer securePort) {
        return new ClientAndServer(port, securePort);
    }

    public void stop() {
        mockServerRunner.stop();
    }
}
