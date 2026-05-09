package org.mockserver.socket;

import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class PortFactory {

    private static final Random random = new Random();

    public static int findFreePort() {
        int[] freePorts = findAvailablePorts(1);
        return freePorts[random.nextInt(freePorts.length)];
    }

    /**
     * Find multiple free ports. Ports are selected from a larger pool of recently-available
     * ports to reduce the chance of collisions. Callers should handle {@code BindException}
     * as the returned ports may be claimed by another process before the caller binds them.
     *
     * @param count the number of free ports to find (must be between 1 and 1000 inclusive)
     * @return an array of {@code count} distinct port numbers that were recently free
     * @throws IllegalArgumentException if count is not between 1 and 1000
     */
    public static int[] findFreePorts(int count) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("count must be between 1 and 1000, was: " + count);
        }
        int[] candidates = findAvailablePorts(count);
        int ratio = candidates.length / count;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = candidates[i * ratio];
        }
        return result;
    }

    private static int[] findAvailablePorts(int number) {
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
