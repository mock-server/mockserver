package org.mockserver.socket;

import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class PortFactory {

    private static final Random random = new Random();

    /**
     * @return number of some available port
     */
    public static int findFreePort() {
        int[] freePorts = findSomeFreePorts(1);
        return freePorts[random.nextInt(freePorts.length)];
    }

    /**
     *
     * @param size size of available ports to be found
     * @return numbers of available ports
     */
    public static int[] findFreePorts(int size) {
        int[] freePorts = findFreePorts(size);
        int ratio = freePorts.length/size;
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = freePorts[i*ratio];
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static int[] findSomeFreePorts(int number) {
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
