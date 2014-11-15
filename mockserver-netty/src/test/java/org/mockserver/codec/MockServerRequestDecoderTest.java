package org.mockserver.codec;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.Parameter.param;

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
    public void shouldDecodeURLAndPath() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Host", "random.host");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = ((HttpRequest) output.get(0));
        assertThat(httpRequest.getURL(), is("http://random.host/uri"));
        assertThat(httpRequest.getPath(), is("/uri"));
    }

    @Test
    public void shouldDecodeSecureURLAndPath() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Host", "random.host");

        // when
        new MockServerRequestDecoder(true).decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = ((HttpRequest) output.get(0));
        assertThat(httpRequest.getURL(), is("https://random.host/uri"));
        assertThat(httpRequest.getPath(), is("/uri"));
    }

    @Test
    public void shouldDecodeURLAndPathWithNoHostHeader() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = ((HttpRequest) output.get(0));
        assertThat(httpRequest.getURL(), is("http://localhost/uri"));
        assertThat(httpRequest.getPath(), is("/uri"));
    }

    @Test
    public void shouldDecodeURLAndPathWithFullURL() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://random.host/uri");

        // when
        mockServerRequestDecoder.decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = ((HttpRequest) output.get(0));
        assertThat(httpRequest.getURL(), is("http://random.host/uri"));
        assertThat(httpRequest.getPath(), is("/uri"));
    }

    @Test
    public void shouldDecodeHeaders() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("headerName1", "headerValue1_1");
        fullHttpRequest.headers().add("headerName1", "headerValue1_2");
        fullHttpRequest.headers().add("headerName2", "headerValue2");

        // when
        new MockServerRequestDecoder(true).decode(null, fullHttpRequest, output);

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
        new MockServerRequestDecoder(true).decode(null, fullHttpRequest, output);

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
        new MockServerRequestDecoder(true).decode(null, fullHttpRequest, output);

        // then
        HttpRequest httpRequest = (HttpRequest) output.get(0);
        assertThat(httpRequest.isKeepAlive(), is(false));
    }

    @Test
    public void shouldDecodeCookies() {
        // given
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/uri");
        fullHttpRequest.headers().add("Cookie", "cookieName1=cookieValue1  ; cookieName2=cookieValue2;   ");
        fullHttpRequest.headers().add("Cookie", "cookieName3  =cookieValue3_1; cookieName3=cookieValue3_2");

        // when
        new MockServerRequestDecoder(true).decode(null, fullHttpRequest, output);

        // then
        List<Cookie> cookies = ((HttpRequest) output.get(0)).getCookies();
        assertThat(cookies, containsInAnyOrder(
                cookie("cookieName1", "cookieValue1"),
                cookie("cookieName2", "cookieValue2"),
                cookie("cookieName3", "cookieValue3_1", "cookieValue3_2")
        ));
    }

}
