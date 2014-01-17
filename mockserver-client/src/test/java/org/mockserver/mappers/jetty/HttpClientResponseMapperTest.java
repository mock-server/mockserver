package org.mockserver.mappers.jetty;

import org.apache.commons.lang3.CharEncoding;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpFields;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class HttpClientResponseMapperTest {

    @Test
    public void shouldMapHttpClientResponseToHttpResponse() {
        // given
        Response httpClientResponse = mock(org.eclipse.jetty.client.HttpResponse.class);
        when(httpClientResponse.getStatus()).thenReturn(500);
        HttpFields headers = new HttpFields();
        headers.add("header_name", "header_value");
        headers.add("Set-Cookie", "cookie_name=cookie_value");
        when(httpClientResponse.getHeaders()).thenReturn(headers);

        // when
        HttpResponse httpResponse = new HttpClientResponseMapper().mapHttpClientResponseToHttpResponse(httpClientResponse, "somebody".getBytes());

        // then
        assertEquals(httpResponse.getStatusCode(), new Integer(500));
        assertEquals(httpResponse.getHeaders(), Arrays.asList(
                new Header("header_name", "header_value"),
                new Header("Set-Cookie", "cookie_name=cookie_value")
        ));
        assertEquals(httpResponse.getCookies(), Arrays.asList(
                new Cookie("cookie_name", "cookie_value")
        ));
        assertEquals(new String(httpResponse.getBody(), StandardCharsets.UTF_8), "somebody");
        assertEquals(httpResponse.getBodyAsString(), "somebody");
    }

    @Test
    public void shouldFilterHeader() {
        // given
        Response httpClientResponse = mock(org.eclipse.jetty.client.HttpResponse.class);
        HttpFields headers = new HttpFields();
        headers.add("header_name", "header_value");
        headers.add("Content-Encoding", "gzip");
        headers.add("Content-Length", "1024");
        headers.add("Transfer-Encoding", "chunked");
        when(httpClientResponse.getHeaders()).thenReturn(headers);

        // when
        HttpResponse httpResponse = new HttpClientResponseMapper().mapHttpClientResponseToHttpResponse(httpClientResponse, "".getBytes());

        // then
        assertEquals(httpResponse.getHeaders(), Arrays.asList(
                new Header("header_name", "header_value")
        ));
    }

    @Test
    public void shouldIgnoreIncorrectlyFormattedCookies() {
        // given
        Response httpClientResponse = mock(org.eclipse.jetty.client.HttpResponse.class);
        HttpFields headers = new HttpFields();
        headers.add("Set-Cookie", "valid_name=valid_value");
        headers.add("Set-Cookie", "=invalid");
        headers.add("Set-Cookie", "valid_name=");
        headers.add("Set-Cookie", "invalid");
        headers.add("Set-Cookie", "");
        when(httpClientResponse.getHeaders()).thenReturn(headers);

        // when
        HttpResponse httpResponse = new HttpClientResponseMapper().mapHttpClientResponseToHttpResponse(httpClientResponse, "".getBytes());

        // then
        assertEquals(httpResponse.getCookies(), Arrays.asList(
                new Cookie("valid_name", "valid_value"),
                new Cookie("valid_name", "")
        ));
    }
}
