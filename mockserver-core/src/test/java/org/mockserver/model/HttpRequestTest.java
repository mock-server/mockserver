package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.model.HttpRequest.request;

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
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(new Parameter("name", "value")).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameters(Arrays.asList(new Parameter("name", "value"))).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value")).getQueryStringParameters().get(0));
        assertEquals(new Parameter("name", "value_one", "value_two"), new HttpRequest().withQueryStringParameter(new Parameter("name", "value_one")).withQueryStringParameter(new Parameter("name", "value_two")).getQueryStringParameters().get(0));
    }

    @Test
    public void returnsBody() {
        assertEquals(new StringBody("somebody", Body.Type.STRING), new HttpRequest().withBody(new StringBody("somebody", Body.Type.STRING)).getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeaders(new Header("name", "value_one", "value_two")).getHeaders().get(0));
        assertEquals(new Header("name", (Collection<String>) null), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
        assertEquals(new Header("name"), new HttpRequest().withHeaders(new Header("name")).getHeaders().get(0));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookies(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookies(new Cookie("name", null)).getCookies().get(0));

        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpRequest().withCookie(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpRequest().withCookie(new Cookie("name", null)).getCookies().get(0));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                request()
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .toString()
        );
    }

    @Test
    public void shouldBeAbleToCompareHttpRequest(){
        List<HttpRequest> collection = new ArrayList<HttpRequest>();
        collection.add(
                request()
                        .withPath("/requestWith3Criteria")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
        );

        collection.add(
                request()
                        .withPath("/requestWith2Criteria")
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
        );

        List<Parameter> strings = Arrays.asList(new Parameter("ParamOne", "Value"), new Parameter("ParamTwo", "Value"));
        collection.add(
                request()
                        .withPath("/requestWith4Criteria")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .withQueryStringParameters(strings)
        );
        Collections.sort(collection);
        assertEquals("/requestWith4Criteria",collection.get(0).path );
        assertEquals("/requestWith3Criteria", collection.get(1).path );
        assertEquals("/requestWith2Criteria", collection.get(2).path);
    }
}
