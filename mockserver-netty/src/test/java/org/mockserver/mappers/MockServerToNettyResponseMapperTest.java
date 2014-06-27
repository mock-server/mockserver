package org.mockserver.mappers;

import com.google.common.base.Charsets;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
        httpResponse.withHeaders(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2_1", "headerValue2_2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        DefaultFullHttpResponse defaultFullHttpResponse = new MockServerToNettyResponseMapper().mapMockServerResponseToNettyResponse(httpResponse);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), defaultFullHttpResponse.getStatus().code());
        assertEquals("somebody", defaultFullHttpResponse.content().toString(Charsets.UTF_8));
        assertEquals("headerValue1", defaultFullHttpResponse.headers().get("headerName1"));
        assertThat(defaultFullHttpResponse.headers().getAll("headerName2"), containsInAnyOrder("headerValue2_1", "headerValue2_2"));
        assertEquals(Arrays.asList(
                "cookieName1=cookieValue1",
                "cookieName2=cookieValue2"
        ), defaultFullHttpResponse.headers().getAll("Set-Cookie"));
    }

    @Test
    public void shouldMapMockServerResponseWithNullValuesToNettyResponse() {
        // given
        // - an HttpResponse
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(null);
        httpResponse.withBody((byte[]) null);
        httpResponse.withHeaders((Header[]) null);
        httpResponse.withCookies((Cookie[]) null);

        // when
        DefaultFullHttpResponse defaultFullHttpResponse = new MockServerToNettyResponseMapper().mapMockServerResponseToNettyResponse(httpResponse);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), defaultFullHttpResponse.getStatus().code());
        assertEquals("", defaultFullHttpResponse.content().toString(Charsets.UTF_8));
        assertTrue(defaultFullHttpResponse.headers().isEmpty());
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
