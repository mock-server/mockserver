package org.mockserver.mappers;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.vertx.java.core.http.CaseInsensitiveMultiMap;
import org.vertxtest.http.MockHttpServerRequest;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpServerRequestMapperTest {

    @Test
    public void createHttpRequestFromHttpServerRequest() {
        // given
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withUri("uri")
                        .withMethod("method")
                        .withPath("somepath")
                        .withQuery("name=value")
                        .withParams(
                                new CaseInsensitiveMultiMap()
                                        .add("parameterName1", Arrays.asList("parameterValue1_2", "parameterValue1_1"))
                                        .add("parameterName2", "parameterValue2"))
                        .withHeaders(
                                new CaseInsensitiveMultiMap()
                                        .add("headerName1", Arrays.asList("headerValue1_1", "headerValue1_2"))
                                        .add("headerName2", "headerValue2")
                                        .add("Cookie", Arrays.asList("cookieName1=cookieValue1;", "cookieName2=cookieValue2;"))
                                        .add("Cookie", "cookieName3=cookieValue3;")
                        );

        // when
        HttpRequest httpRequest = new HttpServerRequestMapper().createHttpRequest(httpServerRequest, "somebody".getBytes());

        // then
        assertEquals("method", httpRequest.getMethod());
        assertEquals("uri", httpRequest.getURL());
        assertEquals("somepath", httpRequest.getPath());
        assertEquals("somebody", httpRequest.getBody());
        assertEquals("name=value", httpRequest.getQueryString()
        );
        assertEquals(
                Lists.newArrayList(
                        new Header("Cookie", "cookieName1=cookieValue1;", "cookieName2=cookieValue2;", "cookieName3=cookieValue3;"),
                        new Header("headerName1", "headerValue1_1", "headerValue1_2"),
                        new Header("headerName2", "headerValue2")
                ), httpRequest.getHeaders()
        );
        assertEquals(
                Lists.newArrayList(
                        new Cookie("cookieName1", "cookieValue1"),
                        new Cookie("cookieName2", "cookieValue2"),
                        new Cookie("cookieName3", "cookieValue3")
                ), httpRequest.getCookies()
        );
    }
}
