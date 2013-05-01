package org.mockserver.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpRequestTest {

    @Test
    public void returnsPath() {
        assertEquals("somepath", new HttpRequest().withPath("somepath").getPath());
    }

    @Test
    public void returnsBody() {
        assertEquals("somebody", new HttpRequest().withBody("somebody").getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(new Header("name", "value")).getHeaders().get(0));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookies().get(0));
    }

    @Test
    public void returnsParameters() {
        assertEquals(new Parameter("name", "value"), new HttpRequest().withParameters(new Parameter("name", "value")).getParameters().get(0));
    }

}
