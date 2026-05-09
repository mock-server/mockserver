package org.mockserver.socket;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * @author jamesdbloom
 */
public class PortFactoryTest {

    @Test
    public void shouldFindFreePort() throws IOException {
        // when
        int freePort = PortFactory.findFreePort();

        // then
        ServerSocket serverSocket = new ServerSocket(freePort);
        assertTrue(serverSocket.isBound());
        serverSocket.close();
    }

    @Test
    public void shouldFindMultipleFreePorts() throws IOException {
        // when
        int[] freePorts = PortFactory.findFreePorts(3);

        // then
        assertEquals(3, freePorts.length);
        for (int freePort : freePorts) {
            ServerSocket serverSocket = new ServerSocket(freePort);
            assertTrue(serverSocket.isBound());
            serverSocket.close();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForZeroCount() {
        PortFactory.findFreePorts(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForNegativeCount() {
        PortFactory.findFreePorts(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForExcessiveCount() {
        PortFactory.findFreePorts(1001);
    }

    @Test
    public void shouldFindMultipleFreePortsWithDistinctValues() throws IOException {
        // when
        int[] freePorts = PortFactory.findFreePorts(5);

        // then
        assertEquals(5, freePorts.length);
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        for (int port : freePorts) {
            unique.add(port);
        }
        assertEquals(5, unique.size());
    }

}
