package org.mockserver.mappers;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapperTest {

    @Test
    public void shouldMapHttpServletRequestToHttpRequest() {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "requestURI");
        httpServletRequest.setQueryString("parameterName=parameterValue");
        httpServletRequest.addHeader("headerName1", "headerValue1_1");
        httpServletRequest.addHeader("headerName1", "headerValue1_2");
        httpServletRequest.addHeader("headerName2", "headerValue2");
        httpServletRequest.setCookies(new javax.servlet.http.Cookie("cookieName1", "cookieValue1"), new javax.servlet.http.Cookie("cookieName2", "cookieValue2"));
        httpServletRequest.setContent("somebody".getBytes());

        // when
        HttpRequest httpRequest = new HttpServletRequestMapper().mapHttpServletRequestToHttpRequest(httpServletRequest);

        // then
        assertEquals("http://localhost:80requestURI?parameterName=parameterValue", httpRequest.getURL());
        assertEquals("requestURI", httpRequest.getPath());
        assertEquals("somebody", httpRequest.getBody());
        assertEquals("parameterName=parameterValue", httpRequest.getQueryString());
        assertEquals(Lists.newArrayList(new Header("headerName1", "headerValue1_1", "headerValue1_2"), new Header("headerName2", "headerValue2")), httpRequest.getHeaders());
        assertEquals(Lists.newArrayList(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2")), httpRequest.getCookies());
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenReadingBody() throws IOException {
        // given
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer("requestURI"));
        when(httpServletRequest.getQueryString()).thenReturn("parameterName=parameterValue");
        Enumeration<String> enumeration = mock(Enumeration.class);
        when(enumeration.hasMoreElements()).thenReturn(false);
        when(httpServletRequest.getHeaderNames()).thenReturn(enumeration);
        when(httpServletRequest.getInputStream()).thenThrow(new IOException());

        // when
        new HttpServletRequestMapper().mapHttpServletRequestToHttpRequest(httpServletRequest);
    }
}
