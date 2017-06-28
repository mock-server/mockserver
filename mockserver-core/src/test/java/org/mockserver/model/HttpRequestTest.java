package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableString.string;

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
        assertEquals(string("somepath"), new HttpRequest().withPath("somepath").getPath());
    }

    @Test
    public void returnsMethod() {
        assertEquals(string("POST"), new HttpRequest().withMethod("POST").getMethod());
    }

    @Test
    public void returnsKeepAlive() {
        assertEquals(true, new HttpRequest().withKeepAlive(true).isKeepAlive());
        assertEquals(false, new HttpRequest().withKeepAlive(false).isKeepAlive());
    }

    @Test
    public void returnsSsl() {
        assertEquals(true, new HttpRequest().withSecure(true).isSecure());
        assertEquals(false, new HttpRequest().withSecure(false).isSecure());
    }

    @Test
    public void returnsQueryStringParameters() {
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(new Parameter("name", "value")).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(Arrays.asList(new Parameter("name", "value"))).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value")).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter("name", "value").getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value_one")).withQueryStringParameter(new Parameter("name", "value_two")).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value_one")).withQueryStringParameter("name", "value_two").getQueryStringParameters().get(0));
    }

    @Test
    public void returnsBody() {
        assertEquals(new StringBody("somebody"), new HttpRequest().withBody(new StringBody("somebody")).getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader("name", "value").getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader("name", "value_two").getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeaders(new Header("name", "value_one", "value_two")).getHeaders().get(0));
        assertEquals(new Header("name", (Collection<String>) null), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
        assertEquals(new Header("name"), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
    }

    @Test
    public void returnsFirstHeaders() {
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1")).getFirstHeader("name"));
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1", "value2")).getFirstHeader("name"));
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1", "value2"), new Header("name", "value3")).getFirstHeader("name"));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookies(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookies(new Cookie("name", null)).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookies().get(0));

        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie("name", "value").getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookie(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookie(new Cookie("name", null)).getCookies().get(0));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                        "  \"headers\" : [ {" + NEW_LINE +
                        "    \"name\" : \"name\"," + NEW_LINE +
                        "    \"values\" : [ \"value\" ]" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"cookies\" : [ {" + NEW_LINE +
                        "    \"name\" : \"name\"," + NEW_LINE +
                        "    \"value\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"body\" : \"some_body\"" + NEW_LINE +
                        "}",
                request()
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .toString()
        );
    }

}
