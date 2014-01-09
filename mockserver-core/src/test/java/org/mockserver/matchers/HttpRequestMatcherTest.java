package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcherTest {

    @Test
    public void matchesMatchingPath() {
        assertTrue(new HttpRequestMatcher().withPath("somePath").matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingPathRegex() {
        assertTrue(new HttpRequestMatcher().withPath("someP[a-z]{3}").matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doesNotMatchIncorrectPath() {
        assertFalse(new HttpRequestMatcher().withPath("somepath").matches(new HttpRequest().withPath("pathsome")));
    }

    @Test
    public void doesNotMatchIncorrectPathRegex() {
        assertFalse(new HttpRequestMatcher().withQueryString("someP[a-z]{2}").matches(new HttpRequest().withQueryString("somePath")));
    }

    @Test
    public void matchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher().withQueryString("someQueryString").matches(new HttpRequest().withQueryString("someQueryString")));
    }

    @Test
    public void matchesMatchingQueryStringRegex() {
        assertTrue(new HttpRequestMatcher().withQueryString("someQueryS[a-z]{5}").matches(new HttpRequest().withQueryString("someQueryString")));
    }

    @Test
    public void doesNotMatchIncorrectQueryString() {
        assertFalse(new HttpRequestMatcher().withQueryString("someQueryString").matches(new HttpRequest().withQueryString("someStringQuery")));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegex() {
        assertFalse(new HttpRequestMatcher().withQueryString("someQueryS[a-z]{4}").matches(new HttpRequest().withQueryString("someStringQuery")));
    }

    @Test
    public void matchesMatchingParameters() {
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name", "value"))));
    }

    @Test
    public void matchesMatchingParametersWithRegex() {
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("name", "v[a-z]{4}")).matches(new HttpRequest().withParameters(new Parameter("name", "value"))));
    }

    @Test
    public void parametersMatchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameOne", "valueOne")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo")));
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueTwo")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo")));
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueTwo", "valueThree")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo&nameTwo=valueThree")));
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueTwo")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo&nameTwo=valueThree")));
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueThree")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo&nameTwo=valueThree")));
        assertTrue(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueT[a-z]{0,10}")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo&nameTwo=valueThree")));
    }

    @Test
    public void parametersDoNotMatchIncorrectMatchingQueryString() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("nameOne", "valueOne", "valueTwo")).matches(new HttpRequest().withQueryString("nameOne=valueOne&nameTwo=valueTwo")));
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("nameOne", "valueOne")).matches(new HttpRequest().withQueryString("nameOne=otherValue&nameTwo=valueTwo")));
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("nameTwo", "valueTwo")).matches(new HttpRequest().withQueryString("nameOne=valueOne")));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name1", "value"))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("name", "value")).matches(new HttpRequest().withParameters(new Parameter("name", "value1"))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        assertFalse(new HttpRequestMatcher().withParameters(new Parameter("name", "va[0-9]{1}ue")).matches(new HttpRequest().withParameters(new Parameter("name", "value1"))));
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
    public void matchesMatchingHeadersWithRegex() {
        assertTrue(new HttpRequestMatcher().withHeaders(new Header("name", ".*")).matches(new HttpRequest().withHeaders(new Header("name", "value"))));
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
    public void doesNotMatchIncorrectHeaderValueRegex() {
        assertFalse(new HttpRequestMatcher().withHeaders(new Header("name", "[0-9]{0,100}")).matches(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void matchesMatchingCookies() {
        assertTrue(new HttpRequestMatcher().withCookies(new Cookie("name", "value")).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void matchesMatchingCookiesWithRegex() {
        assertTrue(new HttpRequestMatcher().withCookies(new Cookie("name", "[a-z]{0,20}lue")).matches(new HttpRequest().withCookies(new Cookie("name", "value"))));
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
    public void doesNotMatchIncorrectCookieValueRegex() {
        assertFalse(new HttpRequestMatcher().withCookies(new Cookie("name", "[A-Z]{0,10}")).matches(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }
}
