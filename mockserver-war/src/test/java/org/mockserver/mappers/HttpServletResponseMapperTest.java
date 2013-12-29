package org.mockserver.mappers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class HttpServletResponseMapperTest {

    @Test
    public void mapHttpServletResponseFromHttpResponse() throws UnsupportedEncodingException {
        // given
        // - an HttpResponse
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatusCode.OK_200.code());
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));
        // - an HttpServletResponse
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new HttpServletResponseMapper().mapHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), httpServletResponse.getStatus());
        assertEquals("somebody", httpServletResponse.getContentAsString());
        assertEquals("headerValue1", httpServletResponse.getHeader("headerName1"));
        assertEquals("headerValue2", httpServletResponse.getHeader("headerName2"));
        assertEquals("cookieValue1", httpServletResponse.getCookie("cookieName1").getValue());
        assertEquals("cookieValue2", httpServletResponse.getCookie("cookieName2").getValue());
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenReadingBody() throws IOException {
        // given
        // - an HttpResponse
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatusCode.OK_200.code());
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));
        // - an HttpServletResponse
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        when(httpServletResponse.getOutputStream()).thenThrow(new IOException());

        // when
        new HttpServletResponseMapper().mapHttpServletResponse(httpResponse, httpServletResponse);
    }
}
