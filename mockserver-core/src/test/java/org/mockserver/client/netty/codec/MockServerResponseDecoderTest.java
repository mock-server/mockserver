package org.mockserver.client.netty.codec;

import com.google.common.net.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class MockServerResponseDecoderTest {

    private MockServerResponseDecoder mockServerResponseDecoder;
    private List<Object> output;
    private FullHttpResponse fullHttpResponse;

    @Before
    public void setupFixture() {
        mockServerResponseDecoder = new MockServerResponseDecoder();
        output = new ArrayList<Object>();
    }

    @Test
    public void shouldDecodeStatusCode() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        HttpResponse httpResponse = (HttpResponse) output.get(0);
        assertThat(httpResponse.getStatusCode(), is(HttpResponseStatus.METHOD_NOT_ALLOWED.code()));
    }

    @Test
    public void shouldDecodeHeaders() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.headers().add("headerName1", "headerValue1_1");
        fullHttpResponse.headers().add("headerName1", "headerValue1_2");
        fullHttpResponse.headers().add("headerName2", "headerValue2");

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        List<Header> headers = ((HttpResponse) output.get(0)).getHeaderList();
        assertThat(headers, containsInAnyOrder(
                header("headerName1", "headerValue1_1", "headerValue1_2"),
                header("headerName2", "headerValue2")
        ));
    }

    @Test
    public void shouldDecodeCookies() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.headers().add("Cookie", "cookieName1=cookieValue1  ; cookieName2=cookieValue2;   ");
        fullHttpResponse.headers().add("Cookie", "cookieName3  =cookieValue3_1; cookieName4=cookieValue3_2");

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        List<Cookie> cookies = ((HttpResponse) output.get(0)).getCookieList();
        assertThat(cookies, containsInAnyOrder(
                cookie("cookieName1", "cookieValue1"),
                cookie("cookieName2", "cookieValue2"),
                cookie("cookieName3", "cookieValue3_1"),
                cookie("cookieName4", "cookieValue3_2")
        ));
    }

    @Test
    public void shouldDecodeCookiesWithEmbeddedEquals() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.headers().add("Cookie", "cookieName1=cookie=Value1  ; cookieName2=cookie==Value2;   ");

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        List<Cookie> cookies = ((HttpResponse) output.get(0)).getCookieList();
        assertThat(cookies, containsInAnyOrder(
                cookie("cookieName1", "cookie=Value1"),
                cookie("cookieName2", "cookie==Value2")
        ));
    }

    @Test
    public void shouldDecodeUTF8Body() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("some_random_string".getBytes(UTF_8)));
        fullHttpResponse.headers().add(CONTENT_TYPE, MediaType.create("text", "plain").toString());

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        Body body = ((HttpResponse) output.get(0)).getBody();
        assertThat(body, Is.<Body>is(exact("some_random_string")));
    }

    @Test
    public void shouldDecodeUTF16Body() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("我说中国话".getBytes(StandardCharsets.UTF_16)));
        fullHttpResponse.headers().add(CONTENT_TYPE, MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString());

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        Body body = ((HttpResponse) output.get(0)).getBody();
        assertThat(body, Is.<Body>is(exact("我说中国话")));
    }

    @Test
    public void shouldDecodeBinaryBody() {
        // given
        fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("some_random_bytes".getBytes(UTF_8)));
        fullHttpResponse.headers().add(CONTENT_TYPE, "image/jpeg");

        // when
        mockServerResponseDecoder.decode(null, fullHttpResponse, output);

        // then
        Body body = ((HttpResponse) output.get(0)).getBody();
        assertThat(body, Is.<Body>is(binary("some_random_bytes".getBytes(UTF_8))));
    }

}
