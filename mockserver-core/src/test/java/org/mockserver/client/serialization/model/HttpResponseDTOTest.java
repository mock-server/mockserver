package org.mockserver.client.serialization.model;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        List<CookieDTO> cookies = Arrays.asList(new CookieDTO(new Cookie("name", "value")));
        List<HeaderDTO> headers = Arrays.asList(new HeaderDTO(new Header("name", "value")));
        Integer statusCode = 200;
        HttpResponse httpRequest = new HttpResponse()
                .withBody("body")
                .withCookies(new Cookie("name", "value"))
                .withHeaders(new Header("name", "value"))
                .withStatusCode(statusCode);

        // when
        HttpResponseDTO httpRequestDTO = new HttpResponseDTO(httpRequest);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getStatusCode(), is(statusCode));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String body = "body";
        Cookie cookie = new Cookie("name", "value");
        Header header = new Header("name", "value");
        Integer statusCode = 200;
        HttpResponse httpRequest = new HttpResponse()
                .withBody(body)
                .withCookies(cookie)
                .withHeaders(header)
                .withStatusCode(statusCode);

        // when
        HttpResponse builtHttpResponse = new HttpResponseDTO(httpRequest).buildObject();

        // then
        assertThat(builtHttpResponse.getBody(), Is.<org.mockserver.model.Body>is(exact(body)));
        assertThat(builtHttpResponse.getCookies(), containsInAnyOrder(cookie));
        assertThat(builtHttpResponse.getHeaders(), containsInAnyOrder(header));
        assertThat(builtHttpResponse.getStatusCode(), is(statusCode));
    }

    @Test
    public void shouldReturnValueSetInSetter() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        List<CookieDTO> cookies = Arrays.asList(new CookieDTO());
        List<HeaderDTO> headers = Arrays.asList(new HeaderDTO());
        Integer statusCode = 200;
        HttpResponse httpRequest = new HttpResponse();

        // when
        HttpResponseDTO httpRequestDTO = new HttpResponseDTO(httpRequest);
        httpRequestDTO.setBody(body);
        httpRequestDTO.setCookies(cookies);
        httpRequestDTO.setHeaders(headers);
        httpRequestDTO.setStatusCode(statusCode);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getStatusCode(), is(statusCode));
    }


    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpResponseDTO httpRequestDTO = new HttpResponseDTO(null);

        // then
        assertThat(httpRequestDTO.getBody(), is(nullValue()));
        assertThat(httpRequestDTO.getCookies(), is(empty()));
        assertThat(httpRequestDTO.getHeaders(), is(empty()));
        assertThat(httpRequestDTO.getStatusCode(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpResponseDTO httpRequestDTO = new HttpResponseDTO(new HttpResponse());

        // then
        assertThat(httpRequestDTO.getBody(), is(BodyDTO.createDTO(exact(""))));
        assertThat(httpRequestDTO.getCookies(), is(empty()));
        assertThat(httpRequestDTO.getHeaders(), is(empty()));
        assertThat(httpRequestDTO.getStatusCode(), is(200));
    }
}
