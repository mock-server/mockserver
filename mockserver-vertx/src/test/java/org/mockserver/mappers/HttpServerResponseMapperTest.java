package org.mockserver.mappers;

import org.apache.commons.lang3.CharEncoding;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.vertxtest.http.MockHttpServerResponse;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpServerResponseMapperTest {

    @Test
    public void shouldMapHttpResponseToHttpServerResponse() {
        // given
        MockHttpServerResponse httpServerResponse = new MockHttpServerResponse();
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.withStatusCode(HttpStatusCode.OK_200.code());
        httpResponse.withBody("somebody");
        httpResponse.withHeaders(new Header("headerName1", "headerValue1_1", "headerValue1_2"), new Header("headerName2", "headerValue2"));
        httpResponse.withCookies(new Cookie("cookieName1", "cookieValue1"), new Cookie("cookieName2", "cookieValue2"));

        // when
        new HttpServerResponseMapper().mapHttpResponseToHttpServerResponse(httpResponse, httpServerResponse);

        // then
        assertEquals(httpServerResponse.getStatusCode(), HttpStatusCode.OK_200.code());
        assertEquals(new String(httpServerResponse.body(), Charset.forName(CharEncoding.UTF_8)), "somebody");
        assertEquals(httpServerResponse.headers().getAll("headerName1"), Arrays.asList("headerValue1_1", "headerValue1_2"));
        assertEquals(httpServerResponse.headers().get("headerName2"), "headerValue2");
        assertEquals(httpServerResponse.headers().getAll("Set-Cookie"), Arrays.asList("cookieName1=cookieValue1", "cookieName2=cookieValue2"));
    }
}
