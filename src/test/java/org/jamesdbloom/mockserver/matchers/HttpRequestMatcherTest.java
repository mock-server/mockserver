package org.jamesdbloom.mockserver.matchers;

import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.Parameter;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherTest {

    @Test
    public void matchesMatchingPath() {
        assertTrue(new HttpRequestMatcher().withPath("somepath").matches(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void matchesMatchingPathRegex() {
        assertTrue(new HttpRequestMatcher().withPath("some[a-z]{4}").matches(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        assertFalse(new HttpRequestMatcher().withPath("somepath").matches(new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        assertFalse(new HttpRequestMatcher().withPath("some[a-z]{3}").matches(new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpRequestMatcher().withBody("somebody").matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpRequestMatcher().withBody("some[a-z]{4}").matches(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpRequestMatcher().withBody("somebody").matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpRequestMatcher().withBody("some[a-z]{3}").matches(new HttpRequest().withBody("bodysome")));
    }

    @Test
    public void matchesMatchingHeaders() {
        assertTrue(new HttpRequestMatcher().withHeaders(new Header("name", "value")).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderName() {
        assertFalse(new HttpRequestMatcher().withHeaders(new Header("name", "value")).matches(new HttpRequest().withHeaders(new Header("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectHeaderValue() {
        assertFalse(new HttpRequestMatcher().withHeaders(new Header("name", "value")).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpRequestMatcher().withCookies(new Cookie("name", "value")).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieName() {
        assertFalse(new HttpRequestMatcher().withCookies(new Cookie("name", "value")).matches(new HttpRequest().withCookies(new Cookie("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectCookieValue() {
        assertFalse(new HttpRequestMatcher().withCookies(new Cookie("name", "value")).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void matchesMatchingParameters() {
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name", "value1"))));
    }

}
