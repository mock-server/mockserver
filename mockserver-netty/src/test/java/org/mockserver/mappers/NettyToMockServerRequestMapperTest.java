package org.mockserver.mappers;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class NettyToMockServerRequestMapperTest {

    @Test
    public void shouldMapHttpServletRequestToHttpRequest() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/requestURI?queryStringParameterNameOne=queryStringParameterValueOne_One&queryStringParameterNameOne=queryStringParameterValueOne_Two&queryStringParameterNameTwo=queryStringParameterValueTwo_One", Unpooled.wrappedBuffer("1a!@£$%^&*()_+=-".getBytes(Charsets.UTF_8)));
        nettyHttpRequest.headers().add("headerName1", "headerValue1_1");
        nettyHttpRequest.headers().add("headerName1", "headerValue1_2");
        nettyHttpRequest.headers().add("headerName2", "headerValue2");
        nettyHttpRequest.headers().add("Host", "some.random.host");
        nettyHttpRequest.headers().add("Cookie", "cookieName1=cookieValue1  ; cookieName2=cookieValue2;   ");
        nettyHttpRequest.headers().add("Cookie", "cookieName3  =cookieValue3_1; cookieName3=cookieValue3_2");

        // when
        HttpRequest httpRequest = new NettyToMockServerRequestMapper().mapNettyRequestToMockServerRequest(nettyHttpRequest, false);

        // then
        assertEquals("http://some.random.host/requestURI?queryStringParameterNameOne=queryStringParameterValueOne_One&queryStringParameterNameOne=queryStringParameterValueOne_Two&queryStringParameterNameTwo=queryStringParameterValueTwo_One", httpRequest.getURL());
        assertEquals("/requestURI", httpRequest.getPath());
        assertEquals(Arrays.asList(
                new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
        ), httpRequest.getQueryStringParameters());
        assertEquals(Lists.newArrayList(
                new Header("headerName1", "headerValue1_1", "headerValue1_2"),
                new Header("headerName2", "headerValue2"),
                new Header("Host", "some.random.host"),
                new Header("Cookie", "cookieName1=cookieValue1  ; cookieName2=cookieValue2;   ", "cookieName3  =cookieValue3_1; cookieName3=cookieValue3_2")
        ), httpRequest.getHeaders());
        assertEquals(Lists.newArrayList(
                new Cookie("cookieName1", "cookieValue1"),
                new Cookie("cookieName2", "cookieValue2"),
                new Cookie("cookieName3", "cookieValue3_1", "cookieValue3_2")
        ), httpRequest.getCookies());
    }

    @Test
    public void shouldMapSecureHttpServletRequestToURL() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/requestURI");
        nettyHttpRequest.headers().add("Host", "some.random.host");

        // when
        HttpRequest httpRequest = new NettyToMockServerRequestMapper().mapNettyRequestToMockServerRequest(nettyHttpRequest, true);

        // then
        assertEquals("https://some.random.host/requestURI", httpRequest.getURL());
        assertEquals("/requestURI", httpRequest.getPath());
    }

    @Test
    public void shouldMapHttpServletRequestWithNoHostHeaderToURL() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/requestURI");

        // when
        HttpRequest httpRequest = new NettyToMockServerRequestMapper().mapNettyRequestToMockServerRequest(nettyHttpRequest, false);

        // then
        assertEquals("http://localhost/requestURI", httpRequest.getURL());
        assertEquals("/requestURI", httpRequest.getPath());
    }

    @Test
    public void shouldMapHttpServletRequestWithFullURLAsUri() {
        // given
        DefaultFullHttpRequest nettyHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/requestURI");

        // when
        HttpRequest httpRequest = new NettyToMockServerRequestMapper().mapNettyRequestToMockServerRequest(nettyHttpRequest, false);

        // then
        assertEquals("http://localhost/requestURI", httpRequest.getURL());
        assertEquals("/requestURI", httpRequest.getPath());
    }
}
