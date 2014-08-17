package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.StringBody;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.*;

/**
 * @author jamesdbloom
 */
public class HttpResponseMatcherTest {

    @Test
    public void matchesMatchingStatusCode() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withStatusCode(202)).matches(new HttpResponse().withStatusCode(202)));
    }

    @Test
    public void doesNotMatchIncorrectStatusCode() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withStatusCode(202)).matches(new HttpResponse().withStatusCode(500)));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withBody(new StringBody("somebody", Type.STRING))).matches(new HttpResponse().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withBody(exact("somebody"))).matches(new HttpResponse().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withBody(regex("some[a-z]{4}"))).matches(new HttpResponse().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withBody(regex("some[a-z]{3}"))).matches(new HttpResponse().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingBodyXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>";
        assertTrue(new HttpResponseMatcher(new HttpResponse().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))).matches(new HttpResponse().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBodyXPath() {
        String matched = "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>";
        assertFalse(new HttpResponseMatcher(new HttpResponse().withBody(xpath("/element[key = 'some_key' and value = 'some_value']"))).matches(new HttpResponse().withBody(matched)));
    }

    @Test
    public void matchesMatchingJSONBody() {
        String matched = "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}";
        assertTrue(new HttpResponseMatcher(new HttpResponse().withBody(json("{ \"some_field\": \"some_value\" }"))).matches(new HttpResponse().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectJSONBody() {
        String matched = "" +
                "{ " +
                "   \"some_incorrect_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}";
        assertFalse(new HttpResponseMatcher(new HttpResponse().withBody(json("{ \"some_field\": \"some_value\" }"))).matches(new HttpResponse().withBody(matched)));
    }


    @Test
    public void matchesMatchingBinaryBody() {
        byte[] matched = "some binary value".getBytes();
        assertTrue(new HttpResponseMatcher(new HttpResponse().withBody(binary("some binary value".getBytes()))).matches(new HttpResponse().withBody(matched)));
    }

    @Test
    public void doesNotMatchIncorrectBinaryBody() {
        byte[] matched = "some other binary value".getBytes();
        assertFalse(new HttpResponseMatcher(new HttpResponse().withBody(binary("some binary value".getBytes()))).matches(new HttpResponse().withBody(matched)));
    }

    @Test
    public void matchesMatchingHeaders() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withHeaders(new Header("name", "value"))).matches(new HttpResponse().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void matchesMatchingHeadersWithRegex() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withHeaders(new Header("name", ".*"))).matches(new HttpResponse().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withHeaders(new Header("name", "value"))).matches(new HttpResponse().withHeaders(new Header("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValue() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withHeaders(new Header("name", "value"))).matches(new HttpResponse().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValueRegex() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withHeaders(new Header("name", "[0-9]{0,100}"))).matches(new HttpResponse().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withCookies(new Cookie("name", "value"))).matches(new HttpResponse().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void matchesMatchingCookiesWithRegex() {
        assertTrue(new HttpResponseMatcher(new HttpResponse().withCookies(new Cookie("name", "[a-z]{0,20}lue"))).matches(new HttpResponse().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieName() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withCookies(new Cookie("name", "value"))).matches(new HttpResponse().withCookies(new Cookie("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withCookies(new Cookie("name", "value"))).matches(new HttpResponse().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValueRegex() {
        assertFalse(new HttpResponseMatcher(new HttpResponse().withCookies(new Cookie("name", "[A-Z]{0,10}"))).matches(new HttpResponse().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void shouldReturnFormattedResponseWithStringBodyInToString() {
        assertEquals("{" + System.getProperty("line.separator") +
                        "  \"statusCode\" : 200," + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"[A-Z]{0,10}\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                new HttpResponseMatcher(
                        response()
                                .withBody("some_body")
                                .withHeaders(new Header("name", "value"))
                                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                ).toString()
        );
    }

    @Test
    public void shouldReturnFormattedResponseWithJsonBodyInToString() {
        assertEquals("{" + System.getProperty("line.separator") +
                        "  \"statusCode\" : 200," + System.getProperty("line.separator") +
                        "  \"body\" : {" + System.getProperty("line.separator") +
                        "    \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"{ \\\"key\\\": \\\"some_value\\\" }\"" + System.getProperty("line.separator") +
                        "  }," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"[A-Z]{0,10}\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                new HttpResponseMatcher(
                        response()
                                .withBody(json("{ \"key\": \"some_value\" }"))
                                .withHeaders(new Header("name", "value"))
                                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                ).toString()
        );
    }
}
