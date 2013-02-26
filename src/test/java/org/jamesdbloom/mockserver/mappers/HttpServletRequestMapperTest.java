package org.jamesdbloom.mockserver.mappers;

import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapperTest {

    @Test
    public void createHttpRequestFromHttpServletRequest() {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");
        httpServletRequest.addHeader("headerName1", "headerValue1");
        httpServletRequest.addHeader("headerName2", "headerValue2");
        httpServletRequest.setCookies(new javax.servlet.http.Cookie("cookieName1", "cookieValue1"), new javax.servlet.http.Cookie("cookieName2", "cookieValue2"));
        httpServletRequest.setContent("somebody".getBytes());

        // when
        HttpRequest httpRequest = new HttpServletRequestMapper().createHttpRequest(httpServletRequest);

        // then
        assertEquals("somepath", httpRequest.getPath());
        assertEquals("somebody", httpRequest.getBody());
        assertEquals(Lists.newArrayList(new Header("headerName1", "headerValue1"), new Header("headerName2", "headerValue2")), httpRequest.getHeaders());
        assertEquals(Lists.newArrayList(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2")), httpRequest.getCookies());
    }
}
