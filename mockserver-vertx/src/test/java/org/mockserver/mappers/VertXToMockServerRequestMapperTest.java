package org.mockserver.mappers;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;
import org.vertx.java.core.http.CaseInsensitiveMultiMap;
import org.vertxtest.http.MockHttpServerRequest;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class VertXToMockServerRequestMapperTest {

    @Test
    public void shouldMapHttpServerRequestToHttpRequest() {
        // given
        MockHttpServerRequest httpServerRequest =
                new MockHttpServerRequest()
                        .withUri("/uri")
                        .withMethod("method")
                        .withPath("somePath")
                        .withQuery("queryStringParameterName1=queryStringParameterValue1_1&queryStringParameterName1=queryStringParameterValue1_2&queryStringParameterName2=queryStringParameterValue2")
                        .withParams(
                                new CaseInsensitiveMultiMap()
                                        .add("parameterName1", Arrays.asList("parameterValue1_1", "parameterValue1_2"))
                                        .add("parameterName2", "parameterValue2"))
                        .withHeaders(
                                new CaseInsensitiveMultiMap()
                                        .add("headerName1", Arrays.asList("headerValue1_1", "headerValue1_2"))
                                        .add("headerName2", "headerValue2")
                                        .add("Host", "localhost:1234")
                                        .add("Cookie", Arrays.asList("cookieName1=cookieValue1", "cookieName2=cookieValue2"))
                                        .add("Cookie", "cookieName3=cookieValue3")
                        )
                        .withBody("bodyParameterName1=bodyParameterValue1_1&bodyParameterName1=bodyParameterValue1_2&bodyParameterName2=bodyParameterValue2".getBytes());

        // when
        HttpRequest httpRequest = new VertXToMockServerRequestMapper().mapVertXRequestToMockServerRequest(httpServerRequest, "somebody".getBytes());

        // then
        assertEquals("method", httpRequest.getMethod());
        assertEquals("http://localhost:1234/uri", httpRequest.getURL());
        assertEquals("somePath", httpRequest.getPath());
        assertEquals("somebody", httpRequest.getBody().toString());
        assertEquals(Arrays.asList(
                new Parameter("queryStringParameterName1", Arrays.asList("queryStringParameterValue1_1", "queryStringParameterValue1_2")),
                new Parameter("queryStringParameterName2", "queryStringParameterValue2")
        ), httpRequest.getQueryStringParameters());
        assertEquals(
                Lists.newArrayList(
                        new Header("Cookie", "cookieName1=cookieValue1", "cookieName2=cookieValue2", "cookieName3=cookieValue3"),
                        new Header("headerName1", "headerValue1_1", "headerValue1_2"),
                        new Header("headerName2", "headerValue2"),
                        new Header("Host", "localhost:1234")
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
