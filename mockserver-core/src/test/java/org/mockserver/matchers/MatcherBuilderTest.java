package org.mockserver.matchers;

import com.google.common.net.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.server.netty.codec.MockServerRequestDecoder;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.StringBody.exact;

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

    // issue https://github.com/jamesdbloom/mockserver/issues/559
    @Test
    public void bodyMatcherThatHandlesDefaultEncoding() {

        // Using Björk as example string. Using unicode for dev environment compatibility, when there is no UTF-8 used.
        // Body should have Charset NULL, when encoding is ISO-8559-1 - DEFAULT_HTTP_CHARACTER_SET
        String bodyTestString = "ISO 8859-1 characters: Bj\u00F6rk";

        // given
        MockServerRequestDecoder mockServerRequestDecoder = new MockServerRequestDecoder(new MockServerLogger(), false);
        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/uri",
            Unpooled.wrappedBuffer(bodyTestString.getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.withCharset(DEFAULT_HTTP_CHARACTER_SET).toString());

        // when

        HttpRequest httpRequest = mockServerRequestDecoder.decode(fullHttpRequest);

        // and
        HttpRequestMatcher httpRequestMapper = new MatcherBuilder(mockLogFormatter).transformsToMatcher(
            new HttpRequest()
                .withMethod(HttpMethod.GET.name())
                .withPath("uri")
                .withBody(new StringBody(bodyTestString))
        );

        // then
        // decoded httpRequest - because request was with default charset, then body charset is NULL
        assertNull(httpRequest.getBody().getCharset(null));
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
