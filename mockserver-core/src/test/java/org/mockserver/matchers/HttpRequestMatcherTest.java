package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.*;

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
        assertFalse(new HttpRequestMatcher().withPath("someP[a-z]{2}").matches(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void matchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someKey", "someValue")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKeyAndValue() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someV[a-z]{4}")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexKey() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someK[a-z]{2}", "someValue")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringRegexValue() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{4}")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringName() {
        assertFalse(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someKey", "someValue")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someOtherKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringValue() {
        assertFalse(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someKey", "someValue")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someOtherValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKeyAndValue() {
        assertFalse(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someV[a-z]{2}")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexKey() {
        assertFalse(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someK[a-z]{5}", "someValue")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void doesNotMatchIncorrectQueryStringRegexValue() {
        assertFalse(new HttpRequestMatcher().withQueryStringParameters(new Parameter("someKey", "someV[a-z]{2}")).matches(new HttpRequest().withQueryStringParameter(new Parameter("someKey", "someValue"))));
    }

    @Test
    public void matchesMatchingQueryStringParameters() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("name", "value")).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void matchesMatchingQueryStringParametersWithRegex() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("name", "v[a-z]{4}")).matches(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void queryStringParametersMatchesMatchingQueryString() {
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameOne", "valueOne")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameTwo", "valueTwo")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameTwo", "valueTwo", "valueThree")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameTwo", "valueTwo")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameTwo", "valueThree")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
        assertTrue(new HttpRequestMatcher().withQueryStringParameters(new Parameter("nameTwo", "valueT[a-z]{0,10}")).matches(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        )));
    }

    @Test
    public void bodyMatchesMatchingBodyParameters() {
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameOne", "valueOne"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo", "valueThree"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameTwo", "valueTwo"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameTwo", "valueThree"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
        assertTrue(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("nameTwo", "valueT[a-z]{0,10}"))).matches(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
        ))));
    }

    @Test
    public void doesNotMatchIncorrectParameterName() {
        assertFalse(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("name", "value"))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name1", "value")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValue() {
        assertFalse(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("name", "value"))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void doesNotMatchIncorrectParameterValueRegex() {
        assertFalse(new HttpRequestMatcher().withBody(new ParameterBody(new Parameter("name", "va[0-9]{1}ue"))).matches(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void matchesMatchingBody() {
        assertTrue(new HttpRequestMatcher().withBody(new StringBody("somebody", Body.Type.EXACT)).matches(new HttpRequest().withBody(new StringBody("somebody", Body.Type.EXACT))));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(new HttpRequestMatcher().withBody(new StringBody("some[a-z]{4}", Body.Type.REGEX)).matches(new HttpRequest().withBody(new StringBody("somebody", Body.Type.EXACT))));
    }

    @Test
    public void doesNotMatchIncorrectBody() {
        assertFalse(new HttpRequestMatcher().withBody(new StringBody("somebody", Body.Type.REGEX)).matches(new HttpRequest().withBody(new StringBody("bodysome", Body.Type.EXACT))));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(new HttpRequestMatcher().withBody(new StringBody("some[a-z]{3}", Body.Type.REGEX)).matches(new HttpRequest().withBody(new StringBody("bodysome", Body.Type.EXACT))));
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
