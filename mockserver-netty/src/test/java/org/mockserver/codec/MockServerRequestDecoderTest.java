package org.mockserver.codec;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class MockServerRequestDecoderTest {

    private MockServerRequestDecoder mockServerRequestDecoder;
    private List<Object> output;
    private FullHttpRequest fullHttpRequest;

    @Before
    public void setupFixture() {
        mockServerRequestDecoder = new MockServerRequestDecoder(false);
        output = new ArrayList<Object>();
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
        List<Parameter> queryStringParameters = ((HttpRequest) output.get(0)).getQueryStringParameters();
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
        List<Header> headers = ((HttpRequest) output.get(0)).getHeaders();
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
        List<Cookie> cookies = ((HttpRequest) output.get(0)).getCookies();
        assertThat(cookies, containsInAnyOrder(
                cookie("cookieName1", "cookieValue1"),
                cookie("cookieName2", "cookieValue2"),
                cookie("cookieName3", "cookieValue3")
        ));
    }

    @Test
    public void shouldDecodeUTF8Body() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("some_random_string".getBytes()));
        fullHttpRequest.headers().add("Content-Type", "plain/text");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, Is.<Body>is(exact("some_random_string")));
    }

    @Test
    public void shouldDecodeBinaryBody() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri", Unpooled.wrappedBuffer("some_random_bytes".getBytes()));
        fullHttpRequest.headers().add("Content-Type", "image/jpeg");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        Body body = ((HttpRequest) output.get(0)).getBody();
        assertThat(body, Is.<Body>is(binary("some_random_bytes".getBytes())));
    }

}
