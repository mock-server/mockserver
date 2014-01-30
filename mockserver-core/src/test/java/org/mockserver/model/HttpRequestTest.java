package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * @author jamesdbloom
 */
public class HttpRequestTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpRequest().request(), HttpRequest.request());
        assertNotSame(HttpRequest.request(), HttpRequest.request());
    }

    @Test
    public void returnsPath() {
        assertEquals("somepath", new HttpRequest().withPath("somepath").getPath());
    }

    @Test
    public void returnsQueryStringParameters() {
        HttpRequest httpRequest = new HttpRequest().withQueryStringParameter(new Parameter("name", "value"));
        assertEquals(Arrays.asList(new Parameter("name", "value")), httpRequest.getQueryStringParameters());
    }

    @Test
    public void returnsBody() {
        assertEquals(new StringBody("somebody", Body.Type.EXACT), new HttpRequest().withBody(new StringBody("somebody", Body.Type.EXACT)).getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeaders(new Header("name", "value_one", "value_two")).getHeaders().get(0));
        assertEquals(new Header("name", (Collection<String>) null), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
        assertEquals(new Header("name"), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", "value_one", "value_two"), new HttpRequest().withCookies(new Cookie("name", "value_one", "value_two")).getCookies().get(0));
        assertEquals(new Cookie("name", (Collection<String>) null), new HttpRequest().withCookies(new Cookie("name")).getCookies().get(0));
        assertEquals(new Cookie("name"), new HttpRequest().withCookies(new Cookie("name")).getCookies().get(0));
    }

    @Test
    public void shouldReturnPort() {
        assertEquals(80, new HttpRequest().withURL("http://www.host.com/some_path").getPort());
        assertEquals(90, new HttpRequest().withURL("http://www.host.com:90/some_path").getPort());
        assertEquals(443, new HttpRequest().withURL("https://www.host.com/some_path").getPort());
        assertEquals(543, new HttpRequest().withURL("https://www.host.com:543/some_path").getPort());
        assertEquals(80, new HttpRequest().withURL("incorrect_scheme://www.host.com/some_path").getPort());
    }

}
