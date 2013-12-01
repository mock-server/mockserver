package org.mockserver.mappers;

import org.apache.commons.lang3.CharEncoding;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.vertx.java.core.http.CaseInsensitiveMultiMap;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class HttpServerResponseMapperTest {

    @Test
    public void mapHttpServerResponseFromHttpResponse() throws UnsupportedEncodingException {
        // given
        HttpServerResponse httpServerResponse = mock(HttpServerResponse.class);
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatusCode.OK_200.code);
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1_1", "headerValue1_2"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new HttpServerResponseMapper().mapHttpServerResponse(httpResponse, httpServerResponse);

        // then
        verify(httpServerResponse).setStatusCode(HttpStatusCode.OK_200.code);
        verify(httpServerResponse).write("somebody", CharEncoding.UTF_8);
        verify(httpServerResponse).putHeader("headerName1", "headerValue1_1");
        verify(httpServerResponse).putHeader("headerName1", "headerValue1_2");
        verify(httpServerResponse).putHeader("headerName2", "headerValue2");
        verify(httpServerResponse).putHeader("Set-Cookie", "cookieName1=cookieValue1");
        verify(httpServerResponse).putHeader("Set-Cookie", "cookieName2=cookieValue2");
    }
}
