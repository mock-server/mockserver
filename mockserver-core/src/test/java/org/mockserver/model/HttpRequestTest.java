package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Matchers.same;
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
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(new Parameter("name", "value")).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(Arrays.asList(new Parameter("name", "value"))).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value")).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter("name", "value").getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value_one")).withQueryStringParameter(new Parameter("name", "value_two")).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value_one")).withQueryStringParameter("name", "value_two").getQueryStringParameterList().get(0));
    }

    @Test
    public void returnsBody() {
        assertEquals(new StringBody("somebody"), new HttpRequest().withBody(new StringBody("somebody")).getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(new Header("name", "value")).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader("name", "value").getHeaderList().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaderList().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader("name", "value_two").getHeaderList().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeaders(new Header("name", "value_one", "value_two")).getHeaderList().get(0));
        assertEquals(new Header("name", (Collection<String>) null), new HttpRequest().withHeaders(new Header("name")).getHeaderList().get(0));
        assertEquals(new Header("name"), new HttpRequest().withHeaders(new Header("name")).getHeaderList().get(0));
        assertThat(new HttpRequest().withHeaders().getHeaderList(), is(empty()));
    }

    @Test
    public void returnsFirstHeaders() {
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1")).getFirstHeader("name"));
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1", "value2")).getFirstHeader("name"));
        assertEquals("value1", new HttpRequest().withHeaders(new Header("name", "value1", "value2"), new Header("name", "value3")).getFirstHeader("name"));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookies(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookies(new Cookie("name", null)).getCookieList().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookieList().get(0));

        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie("name", "value").getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookie(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookie(new Cookie("name", null)).getCookieList().get(0));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                        "  \"method\" : \"METHOD\"," + NEW_LINE +
                        "  \"path\" : \"some_path\"," + NEW_LINE +
                        "  \"queryStringParameters\" : {" + NEW_LINE +
                        "    \"some_parameter\" : [ \"some_parameter_value\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"headers\" : {" + NEW_LINE +
                        "    \"some_header\" : [ \"some_header_value\" ]" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"cookies\" : {" + NEW_LINE +
                        "    \"some_cookie\" : \"some_cookie_value\"" + NEW_LINE +
                        "  }," + NEW_LINE +
                        "  \"keepAlive\" : true," + NEW_LINE +
                        "  \"secure\" : true," + NEW_LINE +
                        "  \"body\" : \"some_body\"" + NEW_LINE +
                        "}",
                request()
                        .withPath("some_path")
                        .withBody("some_body")
                        .withMethod("METHOD")
                        .withHeaders(new Header("some_header", "some_header_value"))
                        .withCookies(new Cookie("some_cookie", "some_cookie_value"))
                        .withSecure(true)
                        .withQueryStringParameters(new Parameter("some_parameter", "some_parameter_value"))
                        .withKeepAlive(true)
                        .toString()
        );
    }

    @Test
    public void shouldClone() {
        // given
        HttpRequest requestOne = request()
                .withPath("some_path")
                .withBody("some_body")
                .withMethod("METHOD")
                .withHeader("some_header", "some_header_value")
                .withSecure(true)
                .withCookie("some_cookie", "some_cookie_value")
                .withQueryStringParameter("some_parameter", "some_parameter_value")
                .withKeepAlive(true);

        // when
        HttpRequest requestTwo = requestOne.clone();

        // then
        assertThat(requestOne, not(same(requestTwo)));
        assertThat(requestOne, is(requestTwo));
    }

}
