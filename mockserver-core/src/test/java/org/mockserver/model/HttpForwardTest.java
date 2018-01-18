package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpForward.forward;

/**
 * @author jamesdbloom
 */
public class HttpForwardTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpForward().forward(), forward());
        assertNotSame(forward(), forward());
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

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                        "  \"host\" : \"some_host\"," + NEW_LINE +
                        "  \"port\" : 9090," + NEW_LINE +
                        "  \"scheme\" : \"HTTPS\"" + NEW_LINE +
                        "}",
                forward()
                        .withHost("some_host")
                        .withPort(9090)
                        .withScheme(HttpForward.Scheme.HTTPS)
                        .toString()
        );
    }
}
