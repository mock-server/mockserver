package org.jamesdbloom.mockserver.mappers;

import org.eclipse.jetty.http.HttpStatus;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpServletResponseMapperTest {

    @Test
    public void mapHttpServletResponseFromHttpResponse() throws UnsupportedEncodingException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatus.OK_200);
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new HttpServletResponseMapper().mapHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals(HttpStatus.OK_200, httpServletResponse.getStatus());
        assertEquals("somebody", httpServletResponse.getContentAsString());
        assertEquals("headerValue1", httpServletResponse.getHeader("headerName1"));
        assertEquals("headerValue2", httpServletResponse.getHeader("headerName2"));
        assertEquals("cookieValue1", httpServletResponse.getCookie("cookieName1").getValue());
        assertEquals("cookieValue2", httpServletResponse.getCookie("cookieName2").getValue());
    }
}
