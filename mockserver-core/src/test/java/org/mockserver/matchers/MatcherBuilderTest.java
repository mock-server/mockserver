package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MatcherBuilderTest {

    private HttpRequest httpRequest = new HttpRequest()
            .withMethod("GET")
            .withPath("some_path")
            .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
            .withBody(new StringBody("some_body"))
            .withHeaders(new Header("name", "value"))
            .withCookies(new Cookie("name", "value"));
    private MockServerLogger mockLogFormatter;

    @Before
    public void setupTestFixture() {
        mockLogFormatter = mock(MockServerLogger.class);
    }

    @Test
    public void shouldCreateMatcherThatMatchesAllFields() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(httpRequest);

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresMethod() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("")
                        .withPath("some_path")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new StringBody("some_body"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresPath() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new StringBody("some_body"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresQueryString() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryStringParameters()
                        .withBody(new StringBody("some_body"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresBodyParameters() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new ParameterBody())
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresBody() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new StringBody(""))
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresHeaders() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new StringBody("some_body"))
                        .withHeaders()
                        .withCookies(new Cookie("name", "value"))
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresCookies() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
                new HttpRequest()
                        .withMethod("GET")
                        .withPath("some_path")
                        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                        .withBody(new StringBody("some_body"))
                        .withHeaders(new Header("name", "value"))
                        .withCookies()
        );

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }
}
