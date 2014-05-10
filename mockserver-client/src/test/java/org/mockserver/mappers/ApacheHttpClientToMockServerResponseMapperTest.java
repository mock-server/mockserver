package org.mockserver.mappers;

import org.apache.commons.io.Charsets;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class ApacheHttpClientToMockServerResponseMapperTest {

    @Test
    public void shouldMapHttpClientResponseToHttpResponseForBase64() throws IOException {
        // given
        CloseableHttpResponse httpClientResponse = mock(CloseableHttpResponse.class);
        when(httpClientResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 500, "Server Error"));
        when(httpClientResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[]{
                new BasicHeader("header_name", "header_value"),
                new BasicHeader("Set-Cookie", "cookie_name=cookie_value")
        });
        when(httpClientResponse.getEntity()).thenReturn(new StringEntity("somebody"));

        // when
        HttpResponse httpResponse = new ApacheHttpClientToMockServerResponseMapper().mapApacheHttpClientResponseToMockServerResponse(httpClientResponse);

        // then
        assertEquals(httpResponse.getStatusCode(), new Integer(500));
        assertEquals(httpResponse.getHeaders(), Arrays.asList(
                new Header("header_name", "header_value"),
                new Header("Set-Cookie", "cookie_name=cookie_value")
        ));
        assertEquals(httpResponse.getCookies(), Arrays.asList(
                new Cookie("cookie_name", "cookie_value")
        ));
        assertEquals(new String(httpResponse.getBody(), Charsets.UTF_8), "somebody");
        assertEquals(httpResponse.getBodyAsString(), Base64Converter.stringToBase64Bytes("somebody".getBytes()));
        assertArrayEquals(httpResponse.getBody(), "somebody".getBytes());
    }

    @Test
    public void shouldMapHttpClientResponseToHttpResponse() throws IOException {
        // given
        CloseableHttpResponse httpClientResponse = mock(CloseableHttpResponse.class);
        when(httpClientResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 500, "Server Error"));
        when(httpClientResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[]{
                new BasicHeader("header_name", "header_value"),
                new BasicHeader("Set-Cookie", "cookie_name=cookie_value")
        });
        when(httpClientResponse.getEntity()).thenReturn(new StringEntity("some_other_body"));

        // when
        HttpResponse httpResponse = new ApacheHttpClientToMockServerResponseMapper().mapApacheHttpClientResponseToMockServerResponse(httpClientResponse);

        // then
        assertEquals(httpResponse.getStatusCode(), new Integer(500));
        assertEquals(httpResponse.getHeaders(), Arrays.asList(
                new Header("header_name", "header_value"),
                new Header("Set-Cookie", "cookie_name=cookie_value")
        ));
        assertEquals(httpResponse.getCookies(), Arrays.asList(
                new Cookie("cookie_name", "cookie_value")
        ));
        assertEquals(new String(httpResponse.getBody(), Charsets.UTF_8), "some_other_body");
        assertEquals(httpResponse.getBodyAsString(), Base64Converter.stringToBase64Bytes("some_other_body".getBytes()));
        assertArrayEquals(httpResponse.getBody(), "some_other_body".getBytes());
    }

    @Test
    public void shouldFilterHeader() throws IOException {
        // given
        CloseableHttpResponse httpClientResponse = mock(CloseableHttpResponse.class);
        when(httpClientResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 500, "Server Error"));
        when(httpClientResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[]{
                new BasicHeader("header_name", "header_value"),
                new BasicHeader("Content-Encoding", "gzip"),
                new BasicHeader("Content-Length", "1024"),
                new BasicHeader("Transfer-Encoding", "chunked")
        });
        when(httpClientResponse.getEntity()).thenReturn(new StringEntity(""));

        // when
        HttpResponse httpResponse = new ApacheHttpClientToMockServerResponseMapper().mapApacheHttpClientResponseToMockServerResponse(httpClientResponse);

        // then
        assertEquals(httpResponse.getHeaders(), Arrays.asList(
                new Header("header_name", "header_value")
        ));
    }

    @Test
    public void shouldIgnoreIncorrectlyFormattedCookies() throws IOException {
        // given
        CloseableHttpResponse httpClientResponse = mock(CloseableHttpResponse.class);
        when(httpClientResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 500, "Server Error"));
        when(httpClientResponse.getAllHeaders()).thenReturn(new org.apache.http.Header[]{
                new BasicHeader("Set-Cookie", "valid_name=valid_value"),
                new BasicHeader("Set-Cookie", "=invalid"),
                new BasicHeader("Set-Cookie", "valid_name="),
                new BasicHeader("Set-Cookie", "invalid"),
                new BasicHeader("Set-Cookie", "")
        });
        when(httpClientResponse.getEntity()).thenReturn(new StringEntity(""));

        // when
        HttpResponse httpResponse = new ApacheHttpClientToMockServerResponseMapper().mapApacheHttpClientResponseToMockServerResponse(httpClientResponse);

        // then
        assertEquals(httpResponse.getCookies(), Arrays.asList(
                new Cookie("valid_name", "valid_value", "")
        ));
    }
}
