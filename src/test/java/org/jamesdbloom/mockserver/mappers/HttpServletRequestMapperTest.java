package org.jamesdbloom.mockserver.mappers;

import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.Parameter;
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
        httpServletRequest.setQueryString("?queryParameterName1=queryParameterValue1_1&queryParameterName1=queryParameterValue1_2&queryParameterName2=queryParameterValue2&queryParameterName3=queryParameterValue3_1,queryParameterValue3_2");
        httpServletRequest.addHeader("headerName1", "headerValue1_1");
        httpServletRequest.addHeader("headerName1", "headerValue1_2");
        httpServletRequest.addHeader("headerName2", "headerValue2");
        httpServletRequest.setCookies(new javax.servlet.http.Cookie("cookieName1", "cookieValue1"), new javax.servlet.http.Cookie("cookieName2", "cookieValue2"));
        httpServletRequest.setContent("somebody".getBytes());

        // when
        HttpRequest httpRequest = new HttpServletRequestMapper().createHttpRequest(httpServletRequest);

        // then
        assertEquals("somepath", httpRequest.getPath());
        assertEquals("somebody", httpRequest.getBody());
        assertEquals(
                Lists.newArrayList(
                        new Parameter("queryParameterName1", "queryParameterValue1_2", "queryParameterValue1_1"),
                        new Parameter("queryParameterName2", "queryParameterValue2"),
                        new Parameter("queryParameterName3", "queryParameterValue3_1", "queryParameterValue3_2")
                ), httpRequest.getQueryParameters());
        assertEquals(Lists.newArrayList(new Header("headerName1", "headerValue1_1", "headerValue1_2"), new Header("headerName2", "headerValue2")), httpRequest.getHeaders());
        assertEquals(Lists.newArrayList(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2")), httpRequest.getCookies());
    }
}
