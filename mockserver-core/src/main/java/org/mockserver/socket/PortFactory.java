package org.mockserver.socket;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class PortFactory {
    public static int findFreePort() {
        int port;
        try {
            ServerSocket server = new ServerSocket(0);
            port = server.getLocalPort();
            server.close();
            // allow time for the socket to be released
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (Exception e) {
            throw new RuntimeException("Exception while trying to find a free port", e);
        }
        return port;
    }
}
