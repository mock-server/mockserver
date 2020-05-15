package org.mockserver.codec;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class MockServerToNettyRequestEncoderBasicMappingTest {

    private MockServerHttpToNettyRequestEncoder mockServerRequestEncoder;
    private List<Object> output;
    private HttpRequest httpRequest;
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Before
    public void setupFixture() {
        mockServerRequestEncoder = new MockServerHttpToNettyRequestEncoder(mockServerLogger);
        output = new ArrayList<>();
        httpRequest = request();
    }

    @Test
    public void shouldEncodeMethod() {
        // given
        httpRequest.withMethod("OPTIONS");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpMethod method = ((FullHttpRequest) output.get(0)).method();
        assertThat(method, is(HttpMethod.OPTIONS));
    }

    @Test
    public void shouldEncodeQueryParameters() {
        // given
        httpRequest
            .withPath("/uri")
            .withQueryStringParameters(
                param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
            );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).uri();
        assertThat(uri, is("/uri?" +
            "queryStringParameterNameOne=queryStringParameterValueOne_One&" +
            "queryStringParameterNameOne=queryStringParameterValueOne_Two&" +
            "queryStringParameterNameTwo=queryStringParameterValueTwo_One"));
    }

    @Test
    public void shouldEscapeQueryParameters() {
        // given
        httpRequest
            .withPath("/uri")
            .withQueryStringParameters(
                param("parameter name with spaces", "a value with double \"quotes\" and spaces"),
                param("another parameter", "a value with single 'quotes' and spaces")
            );

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).uri();
        assertThat(uri, is("/uri?" +
            "parameter%20name%20with%20spaces=a%20value%20with%20double%20%22quotes%22%20and%20spaces&" +
            "another%20parameter=a%20value%20with%20single%20%27quotes%27%20and%20spaces"));
    }

    @Test
    public void shouldEncodePath() {
        // given
        httpRequest.withPath("/other_path");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        String uri = ((FullHttpRequest) output.get(0)).uri();
        assertThat(uri, is("/other_path"));
    }

    @Test
    public void shouldEncodeHeaders() {
        // given
        httpRequest
            .withHeaders(
                new Header("headerName1", "headerValue1"),
                new Header("headerName2", "headerValue2_1", "headerValue2_2")
            )
            .withHeader(HOST.toString(), "localhost");

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("headerName1"), containsInAnyOrder("headerValue1"));
        assertThat(headers.getAll("headerName2"), containsInAnyOrder("headerValue2_1", "headerValue2_2"));
        assertThat(headers.getAll(HOST.toString()), containsInAnyOrder("localhost"));
    }

    @Test
    public void shouldEncodeNoHeaders() {
        // given
        httpRequest.withHeaders((Header[]) null);

        // when
        mockServerRequestEncoder.encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder(
            "accept-encoding",
            "content-length",
            "connection"
        ));
        assertThat(headers.getAll("Accept-Encoding"), containsInAnyOrder("gzip,deflate"));
        assertThat(headers.getAll("Content-Length"), containsInAnyOrder("0"));
        assertThat(headers.getAll("Connection"), containsInAnyOrder("keep-alive"));
    }

    @Test
    public void shouldEncodeCookies() {
        // given
        httpRequest.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), is(Collections.singletonList("cookieName1=cookieValue1; cookieName2=cookieValue2")));
    }

    @Test
    public void shouldEncodeNoCookies() {
        // given
        httpRequest.withCookies((Cookie[]) null);

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        HttpHeaders headers = ((FullHttpRequest) output.get(0)).headers();
        assertThat(headers.getAll("Cookie"), emptyIterable());
    }

    @Test
    public void shouldEncodeStringBody() {
        // given
        httpRequest.withBody("somebody");

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(StandardCharsets.UTF_8), is("somebody"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), nullValue());
    }

    @Test
    public void shouldEncodeStringBodyWithContentType() {
        // given
        httpRequest.withBody(exact("somebody", MediaType.HTML_UTF_8));

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(StandardCharsets.UTF_8), is("somebody"));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.HTML_UTF_8.toString()));
    }

    @Test
    public void shouldEncodeBinaryBody() {
        // given
        httpRequest.withBody(binary("somebody".getBytes(UTF_8)));

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().array(), is("somebody".getBytes(UTF_8)));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), nullValue());
    }

    @Test
    public void shouldEncodeBinaryBodyWithContentType() {
        // given
        httpRequest.withBody(binary("somebody".getBytes(UTF_8), MediaType.QUICKTIME));

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().array(), is("somebody".getBytes(UTF_8)));
        assertThat(fullHttpRequest.headers().get(CONTENT_TYPE), is(MediaType.QUICKTIME.toString()));
    }

    @Test
    public void shouldEncodeNullBody() {
        // given
        httpRequest.withBody((String) null);

        // when
        new MockServerHttpToNettyRequestEncoder(mockServerLogger).encode(null, httpRequest, output);

        // then
        FullHttpRequest fullHttpRequest = (FullHttpRequest) output.get(0);
        assertThat(fullHttpRequest.content().toString(StandardCharsets.UTF_8), is(""));
    }

}
