package org.mockserver.codec;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class NettyToMockServerRequestDecoderTest {

    private NettyHttpToMockServerRequestDecoder mockServerRequestDecoder;
    private List<Object> output;
    private FullHttpRequest fullHttpRequest;

    @Before
    public void setupFixture() {
        mockServerRequestDecoder = new NettyHttpToMockServerRequestDecoder(new MockServerLogger(), false);
        output = new ArrayList<>();
    }

    @Test
    public void shouldDecodeMethod() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/uri");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        NottableString method = ((HttpRequest) output.get(0)).getMethod();
        assertThat(method, is(string("OPTIONS")));
    }

    @Test
    public void shouldDecodeQueryParameters() {
        // given
        String uri = "/uri?" +
            "queryStringParameterNameOne=queryStringParameterValueOne_One&" +
            "queryStringParameterNameOne=queryStringParameterValueOne_Two&" +
            "queryStringParameterNameTwo=queryStringParameterValueTwo_One";
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        List<Parameter> queryStringParameters = ((HttpRequest) output.get(0)).getQueryStringParameterList();
        assertThat(queryStringParameters, containsInAnyOrder(
            param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
            param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
        ));
    }

    @Test
    public void shouldDecodePath() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = ((HttpRequest) output.get(0));
        assertThat(httpRequest.getPath(), is(string("/uri")));
    }

    @Test
    public void shouldDecodeHeaders() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("headerName1", "headerValue1_1");
        fullHttpRequest.headers().add("headerName1", "headerValue1_2");
        fullHttpRequest.headers().add("headerName2", "headerValue2");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        List<Header> headers = ((HttpRequest) output.get(0)).getHeaderList();
        assertThat(headers, containsInAnyOrder(
            header("headerName1", "headerValue1_1", "headerValue1_2"),
            header("headerName2", "headerValue2")
        ));
    }

    @Test
    public void shouldDecodeIsKeepAlive() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Connection", "keep-alive");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = (HttpRequest) output.get(0);
        assertThat(httpRequest.isKeepAlive(), is(true));
    }

    @Test
    public void shouldDecodeIsNotKeepAlive() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Connection", "close");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = (HttpRequest) output.get(0);
        assertThat(httpRequest.isKeepAlive(), is(false));
    }

    @Test
    public void shouldDecodeCookies() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Cookie", "cookieName1=cookieValue1  ; cookieName2=cookieValue2;   ");
        fullHttpRequest.headers().add("Cookie", "cookieName3  =cookieValue3        ;");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        List<Cookie> cookies = ((HttpRequest) output.get(0)).getCookieList();
        assertThat(cookies, containsInAnyOrder(
            cookie("cookieName1", "cookieValue1  "),
            cookie("cookieName2", "cookieValue2"),
            cookie("cookieName3", "cookieValue3        ")
        ));
    }

    @Test
    public void shouldDecodeCookiesWithEmbeddedEquals() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Cookie", "cookieName1=cookie=Value1  ; cookieName2=cookie==Value2;   ");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        List<Cookie> cookies = ((HttpRequest) output.get(0)).getCookieList();
        assertThat(cookies, containsInAnyOrder(
            cookie("cookieName1", "cookie=Value1  "),
            cookie("cookieName2", "cookie==Value2")
        ));
    }

    /*
     * Test is significant because popular Java REST library Jersey adds $Version=1 to all cookies
     * in line with RFC2965's recommendation (even though RFC2965 is now marked "Obsolete" by
     * RFC6265, this is still common and not hard to handle).
     */
    @Test
    public void shouldDecodeCookiesWithRFC2965StyleAttributes() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Cookie", "$Version=1; Customer=WILE_E_COYOTE; $Path=/acme");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        List<Cookie> cookies = ((HttpRequest) output.get(0)).getCookieList();
        assertThat(cookies, containsInAnyOrder(
            cookie("Customer", "WILE_E_COYOTE")
        ));
    }

    @Test
    public void shouldDecodeBodyWithContentTypeAndNoCharset() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("A normal string with ASCII characters".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.create("text", "plain").toString());

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(exact("A normal string with ASCII characters", MediaType.create("text", "plain"))));
    }

    @Test
    public void shouldDecodeBodyWithNoContentType() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("A normal string with ASCII characters".getBytes(DEFAULT_HTTP_CHARACTER_SET)));

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(exact("A normal string with ASCII characters")));
    }

    @Test
    public void shouldTransmitUnencodableCharacters() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("Euro sign: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.create("text", "plain").toString());

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body.getRawBytes(), is("Euro sign: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        assertThat(body.getValue(), is(new String("Euro sign: \u20AC".getBytes(DEFAULT_HTTP_CHARACTER_SET), DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldUseDefaultCharsetIfCharsetNotSupported() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("A normal string with ASCII characters".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
        fullHttpRequest.headers().add(CONTENT_TYPE, "plain/text; charset=invalid-charset");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(new StringBody("A normal string with ASCII characters", "A normal string with ASCII characters".getBytes(DEFAULT_HTTP_CHARACTER_SET), false, MediaType.parse("plain/text; charset=invalid-charset"))));
    }

    @Test
    public void shouldDecodeBodyWithUTF8ContentType() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("avro işarəsi: \u20AC".getBytes(StandardCharsets.UTF_8)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(exact("avro işarəsi: \u20AC", MediaType.PLAIN_TEXT_UTF_8)));
    }

    @Test
    public void shouldDecodeBodyWithUTF16ContentType() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("我说中国话".getBytes(StandardCharsets.UTF_16)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString());

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(exact("我说中国话", MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16))));
    }

    @Test
    public void shouldDecodeBinaryBody() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("some_random_bytes".getBytes(UTF_8)));
        fullHttpRequest.headers().add(CONTENT_TYPE, MediaType.JPEG);

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, is(binary("some_random_bytes".getBytes(UTF_8), MediaType.JPEG)));
    }

}
