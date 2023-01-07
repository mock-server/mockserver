package org.mockserver.mappers;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("unchecked")
public class HttpServletRequestToMockServerHttpRequestDecoderTest {

    @Test
    public void shouldMapHttpServletRequestToHttpRequest() {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "/requestURI");
        httpServletRequest.setContextPath(null);
        httpServletRequest.setQueryString("queryStringParameterNameOne=queryStringParameterValueOne_One&queryStringParameterNameOne=queryStringParameterValueOne_Two&queryStringParameterNameTwo=queryStringParameterValueTwo_One");
        httpServletRequest.addHeader("headerName1", "headerValue1_1");
        httpServletRequest.addHeader("headerName1", "headerValue1_2");
        httpServletRequest.addHeader("headerName2", "headerValue2");
        httpServletRequest.addHeader("Content-Type", "multipart/form-data");
        httpServletRequest.setCookies(new javax.servlet.http.Cookie("cookieName1", "cookieValue1"), new javax.servlet.http.Cookie("cookieName2", "cookieValue2"));
        httpServletRequest.setContent("bodyParameterNameOne=bodyParameterValueOne_One&bodyParameterNameOne=bodyParameterValueOne_Two&bodyParameterNameTwo=bodyParameterValueTwo_One".getBytes(UTF_8));
        httpServletRequest.setLocalAddr("local_addr");
        httpServletRequest.setLocalPort(1234);
        httpServletRequest.setRemoteHost("remote_addr");

        // when
        HttpRequest httpRequest = new HttpServletRequestToMockServerHttpRequestDecoder(configuration(), new MockServerLogger()).mapHttpServletRequestToMockServerRequest(httpServletRequest);

        // then
        assertEquals(string("/requestURI"), httpRequest.getPath());
        assertEquals(new ParameterBody(
            new Parameter("bodyParameterNameOne", "bodyParameterValueOne_One"),
            new Parameter("bodyParameterNameOne", "bodyParameterValueOne_Two"),
            new Parameter("bodyParameterNameTwo", "bodyParameterValueTwo_One")
        ).toString(), httpRequest.getBody().toString());
        assertEquals(new HashSet<>(Arrays.asList(
            new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
            new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
        )), new HashSet<>(httpRequest.getQueryStringParameterList()));
        assertEquals(Lists.newArrayList(
            new Header("headerName1", "headerValue1_1", "headerValue1_2"),
            new Header("headerName2", "headerValue2"),
            new Header("Content-Type", "multipart/form-data"),
            new Header("Cookie", "cookieName1=cookieValue1; cookieName2=cookieValue2")
        ), httpRequest.getHeaderList());
        assertEquals(Lists.newArrayList(
            new Cookie("cookieName1", "cookieValue1"),
            new Cookie("cookieName2", "cookieValue2")
        ), httpRequest.getCookieList());
        assertThat(httpRequest.getLocalAddress(), equalTo("local_addr:1234"));
        assertThat(httpRequest.getRemoteAddress(), equalTo("remote_addr:80"));
        assertThat(httpRequest.getProtocol(), equalTo(Protocol.HTTP_1_1));
    }

    @Test
    public void shouldMapPathForRequestsWithAContextPath() {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "/requestURI");
        httpServletRequest.setContextPath("contextPath");
        httpServletRequest.setPathInfo("/pathInfo");
        httpServletRequest.setContent("".getBytes(UTF_8));

        // when
        HttpRequest httpRequest = new HttpServletRequestToMockServerHttpRequestDecoder(configuration(), new MockServerLogger()).mapHttpServletRequestToMockServerRequest(httpServletRequest);

        // then
        assertEquals(string("/pathInfo"), httpRequest.getPath());
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
        when(httpServletRequest.getInputStream()).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        new HttpServletRequestToMockServerHttpRequestDecoder(configuration(), new MockServerLogger()).mapHttpServletRequestToMockServerRequest(httpServletRequest);
    }
}
