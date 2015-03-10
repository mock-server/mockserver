package org.mockserver.integration;

import org.apache.commons.io.FileUtils;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;

import java.io.File;
import java.io.IOException;

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

    public ClientAndServer(Integer port, String keystoreLocation, String keystorePassword) {
        this(port);
        try {
            FileUtils.copyFile(new File(keystoreLocation), new File(SSLFactory.KEY_STORE_FILENAME));
        } catch (IOException e) {
            logger.error("Error copying keystore" + e.getMessage());
        }

        SSLFactory.keyStorePassword = keystorePassword;
    }

    public static ClientAndServer startClientAndServer(Integer port) {
        return new ClientAndServer(port);
    }
    public static ClientAndServer startClientAndServer(Integer port, String keystoreLocation, String keystorePassword) {
        return new ClientAndServer(port, keystoreLocation, keystorePassword);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

}
