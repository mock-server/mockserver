package org.mockserver.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * @author jamesdbloom
 */
public class HttpForwardTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpForward().forward(), HttpForward.forward());
        assertNotSame(HttpForward.forward(), HttpForward.forward());
    }

    @Test
    public void returnsPort() {
        assertEquals(new Integer(9090), new HttpForward().withPort(9090).getPort());
    }

    @Test
    public void returnsHost() {
        assertEquals("some_host", new HttpForward().withHost("some_host").getHost());
    }

    @Test
    public void returnsScheme() {
        assertEquals(HttpForward.Scheme.HTTPS, new HttpForward().withScheme(HttpForward.Scheme.HTTPS).getScheme());
    }
}
