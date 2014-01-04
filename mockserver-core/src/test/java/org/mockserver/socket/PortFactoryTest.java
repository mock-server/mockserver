package org.mockserver.socket;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class PortFactoryTest {

    @Test
    public void shouldFindFreePort() throws IOException {
        // when
        int freePort = new PortFactory().findFreePort();

        // then
        assertTrue(new ServerSocket(freePort).isBound());
    }
}
