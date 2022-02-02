package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(request(), HttpRequest.request());
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
        assertEquals(Boolean.TRUE, new HttpRequest().withKeepAlive(true).isKeepAlive());
        assertEquals(Boolean.FALSE, new HttpRequest().withKeepAlive(false).isKeepAlive());
    }

    @Test
    public void returnsSsl() {
        assertEquals(Boolean.TRUE, new HttpRequest().withSecure(true).isSecure());
        assertEquals(Boolean.FALSE, new HttpRequest().withSecure(false).isSecure());
    }

    @Test
    public void returnsPathParameters() {
        assertEquals(new Parameter("name", "value"), new HttpRequest().withPathParameters(new Parameter("name", "value")).getPathParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withPathParameters(Collections.singletonList(new Parameter("name", "value"))).getPathParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withPathParameter(new Parameter("name", "value")).getPathParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withPathParameter("name", "value").getPathParameterList().get(0));
        assertEquals(new Parameter(string("name"), schemaString("{ \"type\": \"string\" }")), new HttpRequest().withSchemaPathParameter("name", "{ \"type\": \"string\" }").getPathParameterList().get(0));
        assertEquals(new Parameter(string("name"), schemaString("{ \"type\": \"string\" }"), schemaString("{ \"type\": \"integer\" }")), new HttpRequest().withSchemaPathParameter("name", "{ \"type\": \"string\" }", "{ \"type\": \"integer\" }").getPathParameterList().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withPathParameter(new Parameter("name", "value_one")).withPathParameter(new Parameter("name", "value_two")).getPathParameterList().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withPathParameter(new Parameter("name", "value_one")).withPathParameter("name", "value_two").getPathParameterList().get(0));
    }

    @Test
    public void returnsQueryStringParameters() {
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(new Parameter("name", "value")).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(Collections.singletonList(new Parameter("name", "value"))).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value")).getQueryStringParameterList().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter("name", "value").getQueryStringParameterList().get(0));
        assertEquals(new Parameter(string("name"), schemaString("{ \"type\": \"string\" }")), new HttpRequest().withSchemaQueryStringParameter("name", "{ \"type\": \"string\" }").getQueryStringParameterList().get(0));
        assertEquals(new Parameter(string("name"), schemaString("{ \"type\": \"string\" }"), schemaString("{ \"type\": \"integer\" }")), new HttpRequest().withSchemaQueryStringParameter("name", "{ \"type\": \"string\" }", "{ \"type\": \"integer\" }").getQueryStringParameterList().get(0));
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
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(Collections.singletonList(new Header("name", "value"))).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader("name", "value").getHeaderList().get(0));
        assertEquals(new Header(string("name"), schemaString("{ \"type\": \"string\" }")), new HttpRequest().withSchemaHeader("name", "{ \"type\": \"string\" }").getHeaderList().get(0));
        assertEquals(new Header(string("name"), schemaString("{ \"type\": \"string\" }"), schemaString("{ \"type\": \"integer\" }")), new HttpRequest().withSchemaHeader("name", "{ \"type\": \"string\" }", "{ \"type\": \"integer\" }").getHeaderList().get(0));
        assertEquals(new Header("name", ".*"), new HttpRequest().withHeader(string("name")).getHeaderList().get(0));
        assertEquals(new Header("name", ".*"), new HttpRequest().withHeader("name").getHeaderList().get(0));
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
    public void shouldContainHeaderByName() {
        assertTrue(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("name"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("names"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("value1"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader(null));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader(""));
    }

    @Test
    public void shouldContainHeaderByNameAndValue() {
        assertTrue(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("name", "value1"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("names", "value1"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("name", "value12"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("value1", "name"));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader(null, null));
        assertFalse(new HttpRequest().withHeaders(new Header("name", "value1")).containsHeader("", ""));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookies(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookies(new Cookie("name", null)).getCookieList().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(Collections.singletonList(new Cookie("name", "value"))).getCookieList().get(0));

        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie("name", "value").getCookieList().get(0));
        assertEquals(new Cookie(string("name"), schemaString("{ \"type\": \"string\" }")), new HttpRequest().withSchemaCookie("name", "{ \"type\": \"string\" }").getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookie(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookie(new Cookie("name", null)).getCookieList().get(0));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                "  \"method\" : \"METHOD\"," + NEW_LINE +
                "  \"path\" : \"some_path\"," + NEW_LINE +
                "  \"pathParameters\" : {" + NEW_LINE +
                "    \"some_path_parameter\" : [ \"some_path_parameter_value\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
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
                .withPathParameters(new Parameter("some_path_parameter", "some_path_parameter_value"))
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
            .withPathParameter("some_path_parameter", "some_path_parameter_value")
            .withQueryStringParameter("some_parameter", "some_parameter_value")
            .withKeepAlive(true);

        // when
        HttpRequest requestTwo = requestOne.clone();

        // then
        assertThat(requestOne, not(sameInstance(requestTwo)));
        assertThat(requestOne, is(requestTwo));
    }

    @Test
    public void shouldUpdate() {
        // given
        HttpRequest requestOne = request()
            .withPath("some_path")
            .withBody("some_body")
            .withMethod("METHOD")
            .withHeader("some_header", "some_header_value")
            .withSecure(true)
            .withCookie("some_cookie", "some_cookie_value")
            .withPathParameter("some_path_parameter", "some_path_parameter_value")
            .withQueryStringParameter("some_parameter", "some_parameter_value")
            .withKeepAlive(true);
        HttpRequest requestTwo = request()
            .withPath("some_path_two")
            .withBody("some_body_two")
            .withMethod("METHO_TWO")
            .withHeader("some_header_two", "some_header_value_two")
            .withSecure(false)
            .withCookie("some_cookie_two", "some_cookie_value_two")
            .withPathParameter("some_path_parameter_two", "some_path_parameter_value_two")
            .withQueryStringParameter("some_parameter_two", "some_parameter_value_two")
            .withKeepAlive(false);

        // when
        requestOne.update(requestTwo, null);

        // then
        assertThat(requestOne, is(
            request()
                .withPath("some_path_two")
                .withBody("some_body_two")
                .withMethod("METHO_TWO")
                .withHeader("some_header", "some_header_value")
                .withHeader("some_header_two", "some_header_value_two")
                .withSecure(false)
                .withCookie("some_cookie", "some_cookie_value")
                .withCookie("some_cookie_two", "some_cookie_value_two")
                .withPathParameter("some_path_parameter", "some_path_parameter_value")
                .withPathParameter("some_path_parameter_two", "some_path_parameter_value_two")
                .withQueryStringParameter("some_parameter", "some_parameter_value")
                .withQueryStringParameter("some_parameter_two", "some_parameter_value_two")
                .withKeepAlive(false)
        ));
    }

    @Test
    public void shouldUpdateEmptyRequest() {
        // given
        HttpRequest requestOne = request();
        HttpRequest requestTwo = request()
            .withPath("some_path_two")
            .withBody("some_body_two")
            .withMethod("METHO_TWO")
            .withHeader("some_header_two", "some_header_value_two")
            .withSecure(false)
            .withCookie("some_cookie_two", "some_cookie_value_two")
            .withPathParameter("some_path_parameter_two", "some_path_parameter_value_two")
            .withQueryStringParameter("some_parameter_two", "some_parameter_value_two")
            .withKeepAlive(false);

        // when
        requestOne.update(requestTwo, null);

        // then
        assertThat(requestOne, is(
            request()
                .withPath("some_path_two")
                .withBody("some_body_two")
                .withMethod("METHO_TWO")
                .withHeader("some_header_two", "some_header_value_two")
                .withSecure(false)
                .withCookie("some_cookie_two", "some_cookie_value_two")
                .withPathParameter("some_path_parameter_two", "some_path_parameter_value_two")
                .withQueryStringParameter("some_parameter_two", "some_parameter_value_two")
                .withKeepAlive(false)
        ));
    }

}
