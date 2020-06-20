package org.mockserver.matchers;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerRequest;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.MediaType.PLAIN_TEXT_UTF_8;

/**
 * @author jamesdbloom
 */
public class MatcherBuilderTest {

    private final HttpRequest httpRequest = new HttpRequest()
        .withMethod("GET")
        .withPath("some_path")
        .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
        .withBody(new StringBody("some_body"))
        .withHeaders(new Header("name", "value"))
        .withCookies(new Cookie("name", "value"));
    private MockServerLogger mockServerLogger;

    @Before
    public void setupTestFixture() {
        mockServerLogger = mock(MockServerLogger.class);
    }

    @Test
    public void shouldCreateMatcherThatMatchesAllFields() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(httpRequest));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldSupportSpecialCharactersWhenCharsetSpecified() {
        String bodyTestString = "UTF_8 characters: Bj\u00F6rk";

        // given
        FullHttpRequestToMockServerRequest fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerRequest(mockServerLogger, false);
        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(
            HTTP_1_1,
            GET,
            "/uri",
            wrappedBuffer(bodyTestString.getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        fullHttpRequest.headers().add(CONTENT_TYPE, PLAIN_TEXT_UTF_8.withCharset(DEFAULT_HTTP_CHARACTER_SET).toString());

        // when
        HttpRequest httpRequest = fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest);

        // and
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(new MockServerLogger()).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod(GET.name())
                .withPath("/uri")
                .withBody(new StringBody(bodyTestString))
        ));

        // then
        assertThat(httpRequest.getBody().getCharset(null), is(DEFAULT_HTTP_CHARACTER_SET));
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldSupportSpecialCharactersWithDefaultCharset() {
        String bodyTestString = "UTF_8 characters: Bj\u00F6rk";

        // given
        FullHttpRequestToMockServerRequest fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerRequest(mockServerLogger, false);
        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(
            HTTP_1_1,
            GET,
            "/uri",
            wrappedBuffer(bodyTestString.getBytes(DEFAULT_HTTP_CHARACTER_SET))
        );

        // when
        HttpRequest httpRequest = fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest);

        // and
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(new MockServerLogger()).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod(GET.name())
                .withPath("/uri")
                .withBody(new StringBody(bodyTestString))
        ));

        // then - request used default charset, then body charset is NULL
        assertNull(httpRequest.getBody().getCharset(null));
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresMethod() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("")
                .withPath("some_path")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new StringBody("some_body"))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresPath() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new StringBody("some_body"))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresQueryString() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("some_path")
                .withQueryStringParameters()
                .withBody(new StringBody("some_body"))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresBodyParameters() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("some_path")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new ParameterBody())
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresBody() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("some_path")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new StringBody(""))
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresHeaders() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("some_path")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new StringBody("some_body"))
                .withHeaders()
                .withCookies(new Cookie("name", "value"))
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }

    @Test
    public void shouldCreateMatcherThatIgnoresCookies() {
        // when
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockServerLogger).transformsToMatcher(new Expectation(
            new HttpRequest()
                .withMethod("GET")
                .withPath("some_path")
                .withQueryStringParameter(new Parameter("queryStringParameterName", "queryStringParameterValue"))
                .withBody(new StringBody("some_body"))
                .withHeaders(new Header("name", "value"))
                .withCookies()
        ));

        // then
        assertTrue(httpRequestMapper.matches(null, httpRequest));
    }
}
