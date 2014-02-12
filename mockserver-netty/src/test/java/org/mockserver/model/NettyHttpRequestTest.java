package org.mockserver.model;

import com.google.common.base.Charsets;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class NettyHttpRequestTest {

    @Test
    public void shouldParseQueryStringParameters() {
        // given
        NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost:9090/somePath?queryStringParameterNameOne=queryStringParameterValueOne_One&queryStringParameterNameOne=queryStringParameterValueOne_Two;queryStringParameterNameTwo=queryStringParameterValueTwo_One", true);

        // when
        Map<String, List<String>> parameters = nettyHttpRequest.parameters();

        // then
        assertEquals("http://localhost:9090/somePath?queryStringParameterNameOne=queryStringParameterValueOne_One&queryStringParameterNameOne=queryStringParameterValueOne_Two;queryStringParameterNameTwo=queryStringParameterValueTwo_One", nettyHttpRequest.getUri());
        assertEquals("http://localhost:9090/somePath", nettyHttpRequest.path());
        assertEquals(Arrays.asList("queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"), parameters.get("queryStringParameterNameOne"));
        assertEquals(Arrays.asList("queryStringParameterValueTwo_One"), parameters.get("queryStringParameterNameTwo"));
    }

    @Test
    public void shouldAddContentChunks() {
        // given
        NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somePath", true);

        // when
        nettyHttpRequest.content(Unpooled.wrappedBuffer("one".getBytes()));
        nettyHttpRequest.content(Unpooled.wrappedBuffer("two".getBytes()));
        nettyHttpRequest.content(Unpooled.wrappedBuffer("three".getBytes()));

        // then
        assertEquals("onetwothree", nettyHttpRequest.content().toString(Charsets.UTF_8));
    }

    @Test
    public void shouldMatchRequests() {
        // when
        NettyHttpRequest nettyHttpRequest = new NettyHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somePath?queryStringParameterNameOne=queryStringParameterValueOne", true);

        // then
        assertTrue(nettyHttpRequest.matches(HttpMethod.GET, "/somePath"));
        assertFalse(nettyHttpRequest.matches(HttpMethod.PUT, "/somePath"));
        assertFalse(nettyHttpRequest.matches(HttpMethod.GET, "/someOtherPath"));
    }

    @Test
    public void shouldSetSecure() {
        assertTrue(new NettyHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somePath", true).isSecure());
        assertFalse(new NettyHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/somePath", false).isSecure());
    }
}
