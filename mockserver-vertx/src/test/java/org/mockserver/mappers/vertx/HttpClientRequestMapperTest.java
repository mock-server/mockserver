package org.mockserver.mappers.vertx;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author jamesdbloom
 */
public class HttpClientRequestMapperTest {

    @Test
    public void shouldMapHttpRequestToHttpClientRequest() {
        // given
        HttpClientRequestMapper httpClientRequestMapper = new HttpClientRequestMapper();
        HttpClientRequest httpClientRequest = mock(HttpClientRequest.class);

        // when
        httpClientRequestMapper.mapHttpRequestToHttpClientRequest(
                new HttpRequest()
                        .withBody("some_body")
                        .withHeaders(
                                new Header("some_header_name_one", "some_header_value_one_one", "some_header_value_one_two"),
                                new Header("some_header_name_two", "some_header_value_two")
                        )
                        .withCookies(
                                new Cookie("some_cookie_name_one", "some_cookie_value_one_one", "some_cookie_value_one_two"),
                                new Cookie("some_cookie_name_two", "some_cookie_value_two")
                        ), httpClientRequest);

        // then
        verify(httpClientRequest).putHeader("some_header_name_one", "some_header_value_one_one");
        verify(httpClientRequest).putHeader("some_header_name_one", "some_header_value_one_two");
        verify(httpClientRequest).putHeader("some_header_name_two", "some_header_value_two");
        verify(httpClientRequest).putHeader("Set-Cookie", "some_cookie_name_one=some_cookie_value_one_one");
        verify(httpClientRequest).putHeader("Set-Cookie", "some_cookie_name_one=some_cookie_value_one_two");
        verify(httpClientRequest).putHeader("Set-Cookie", "some_cookie_name_two=some_cookie_value_two");
        verify(httpClientRequest).putHeader("Content-Length", "" + "some_body".length());
        verify(httpClientRequest).setChunked(false);
        verify(httpClientRequest).write(new Buffer("some_body"));
    }
}
