package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MatcherBuilderTest {

    private HttpRequest httpRequest = new HttpRequest()
            .withMethod("GET")
            .withPath("some_path")
            .withQueryString("query_string")
            .withURL("url")
            .withBody("some_body")
            .withHeaders(new Header("name", "value"))
            .withCookies(new Cookie("name", "value"));;

    @Test
    public void shouldCreateMatcherThatMatchesAllFields() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(httpRequest);

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresMethod() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("")
                        .withPath("some_path")
                        .withQueryString("query_string")
                        .withURL("url")
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresPath() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("")
                        .withQueryString("query_string")
                        .withURL("url")
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresQueryString() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryString("")
                        .withURL("url")
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresURL() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryString("query_string")
                        .withURL("")
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresBody() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryString("query_string")
                        .withURL("url")
                        .withBody("")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresHeaders() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryString("query_string")
                        .withURL("url")
                        .withBody("some_body")
                        .withHeaders()
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresCookies() {
        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryString("query_string")
                        .withURL("url")
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies()
        );

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }
}
