package org.mockserver.socket;

import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class PortFactory {

    private static Random random = new Random();

    public static int findFreePort() {
        int[] freePorts = findFreePorts(1);
        return freePorts[random.nextInt(freePorts.length)];
    }

    @SuppressWarnings("SameParameterValue")
    private static int[] findFreePorts(int number) {
        int arraySize = number + random.nextInt(60);
        int[] port = new int[arraySize];
        ServerSocket[] serverSockets = new ServerSocket[arraySize];
        try {
            for (int i = port.length - 1; i >= 0; i--) {
                serverSockets[i] = new ServerSocket(0);
                port[i] = serverSockets[i].getLocalPort();
            }
            for (ServerSocket serverSocket : serverSockets) {
                serverSocket.close();
            }
            // allow time for the socket to be released
            TimeUnit.MILLISECONDS.sleep(250);
        } catch (Exception e) {
            throw new RuntimeException("Exception while trying to find a free port", e);
        }
        return port;
    }
}
