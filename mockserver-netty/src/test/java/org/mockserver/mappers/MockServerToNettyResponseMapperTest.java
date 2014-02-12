package org.mockserver.mappers;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.apache.commons.io.Charsets;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MockServerToNettyResponseMapperTest {

    @Test
    public void shouldMapMockServerResponseToNettyResponse() {
        // given
        // - an HttpResponse
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatusCode.OK_200.code());
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        DefaultFullHttpResponse defaultFullHttpResponse = new MockServerToNettyResponseMapper().mapMockServerResponseToNettyResponse(httpResponse);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), defaultFullHttpResponse.getStatus().code());
        assertEquals("somebody", defaultFullHttpResponse.content().toString(Charsets.UTF_8));
        assertEquals("headerValue1", defaultFullHttpResponse.headers().get("headerName1"));
        assertEquals("headerValue2", defaultFullHttpResponse.headers().get("headerName2"));
        assertEquals(Arrays.asList(
                "cookieName1=cookieValue1",
                "cookieName2=cookieValue2"
        ), defaultFullHttpResponse.headers().getAll("Set-Cookie"));
    }

    @Test
    public void shouldMapNullResponseToNettyResponse() {
        // when
        DefaultFullHttpResponse defaultFullHttpResponse = new MockServerToNettyResponseMapper().mapMockServerResponseToNettyResponse(null);

        // then
        assertEquals(HttpStatusCode.NOT_FOUND_404.code(), defaultFullHttpResponse.getStatus().code());
        assertEquals("", defaultFullHttpResponse.content().toString(Charsets.UTF_8));
        assertTrue(defaultFullHttpResponse.headers().isEmpty());
    }
}
