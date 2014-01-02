package org.mockserver.mappers.vertx;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.vertx.java.core.http.CaseInsensitiveMultiMap;
import org.vertx.java.core.http.HttpClientResponse;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author jamesdbloom
 */
public class HttpClientResponseMapperTest {

    @Test
    public void shouldMapHttpServerResponseToHttpResponse() {
        // given
        HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
        HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        when(httpClientResponse.statusCode()).thenReturn(100);
        when(httpClientResponse.headers()).thenReturn(new CaseInsensitiveMultiMap()
                .add("some_header_name_one", Arrays.asList("some_header_value_one_one", "some_header_value_one_two"))
                .add("some_header_name_two", "some_header_value_two")
                .add("Cookie", "some_cookie_name_one=some_cookie_value_one_one")
                .add("Set-Cookie", "some_cookie_name_one=some_cookie_value_one_two")
                .add("Set-Cookie", "some_cookie_name_two=some_cookie_value_two")
        );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.CONTINUE_100.code())
                        .withBody("some_body")
                        .withHeaders(
                                new Header("Cookie", "some_cookie_name_one=some_cookie_value_one_one"),
                                new Header("Set-Cookie", "some_cookie_name_one=some_cookie_value_one_two", "some_cookie_name_two=some_cookie_value_two"),
                                new Header("some_header_name_one", "some_header_value_one_one", "some_header_value_one_two"),
                                new Header("some_header_name_two", "some_header_value_two")
                        )
                        .withCookies(
                                new Cookie("some_cookie_name_one", "some_cookie_value_one_one"),
                                new Cookie("some_cookie_name_one", "some_cookie_value_one_two"),
                                new Cookie("some_cookie_name_two", "some_cookie_value_two")
                        ),
                httpClientResponseMapper.mapHttpClientResponseToHttpResponse(httpClientResponse, "some_body".getBytes()));
    }
}
