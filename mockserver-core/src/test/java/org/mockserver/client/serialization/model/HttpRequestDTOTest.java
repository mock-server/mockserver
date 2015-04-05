package org.mockserver.client.serialization.model;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

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
public class HttpRequestDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        List<CookieDTO> cookies = Arrays.asList(new CookieDTO(new Cookie("name", "value"), false));
        List<HeaderDTO> headers = Arrays.asList(new HeaderDTO(new Header("name", "value"), false));
        String method = "METHOD";
        String path = "path";
        List<ParameterDTO> queryStringParameters = Arrays.asList(new ParameterDTO(new Parameter("name", "value"), false));
        HttpRequest httpRequest = new HttpRequest()
                .withBody("body")
                .withCookies(new Cookie("name", "value"))
                .withHeaders(new Header("name", "value"))
                .withMethod(method)
                .withPath(path)
                .withQueryStringParameter(new Parameter("name", "value"));

        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(httpRequest, false);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getMethod(), is(method));
        assertThat(httpRequestDTO.getPath(), is(path));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(queryStringParameters));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String body = "body";
        Cookie cookie = new Cookie("name", "value");
        Header header = new Header("name", "value");
        String method = "METHOD";
        String path = "path";
        Parameter parameter = new Parameter("name", "value");
        HttpRequest httpRequest = new HttpRequest()
                .withBody(body)
                .withCookies(cookie)
                .withHeaders(header)
                .withMethod(method)
                .withPath(path)
                .withQueryStringParameter(parameter);

        // when
        HttpRequest builtHttpRequest = new HttpRequestDTO(httpRequest, false).buildObject();

        // then
        assertThat(builtHttpRequest.getBody(), Is.<org.mockserver.model.Body>is(exact(body)));
        assertThat(builtHttpRequest.getCookies(), containsInAnyOrder(cookie));
        assertThat(builtHttpRequest.getHeaders(), containsInAnyOrder(header));
        assertThat(builtHttpRequest.getMethod(), is(method));
        assertThat(builtHttpRequest.getPath(), is(path));
        assertThat(builtHttpRequest.getQueryStringParameters(), containsInAnyOrder(parameter));
    }

    @Test
    public void shouldReturnValueSetInSetter() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        List<CookieDTO> cookies = Arrays.asList(new CookieDTO());
        List<HeaderDTO> headers = Arrays.asList(new HeaderDTO());
        String method = "METHOD";
        String path = "path";
        List<ParameterDTO> queryStringParameters = Arrays.asList(new ParameterDTO());
        HttpRequest httpRequest = new HttpRequest();

        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(httpRequest, false);
        httpRequestDTO.setBody(body);
        httpRequestDTO.setCookies(cookies);
        httpRequestDTO.setHeaders(headers);
        httpRequestDTO.setMethod(method);
        httpRequestDTO.setPath(path);
        httpRequestDTO.setQueryStringParameters(queryStringParameters);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getMethod(), is(method));
        assertThat(httpRequestDTO.getPath(), is(path));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(queryStringParameters));
    }


    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(null, false);

        // then
        assertThat(httpRequestDTO.getBody(), is(nullValue()));
        assertThat(httpRequestDTO.getCookies(), is(empty()));
        assertThat(httpRequestDTO.getHeaders(), is(empty()));
        assertThat(httpRequestDTO.getMethod(), is(""));
        assertThat(httpRequestDTO.getPath(), is(""));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(empty()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(new HttpRequest(), false);

        // then
        assertThat(httpRequestDTO.getBody(), is(nullValue()));
        assertThat(httpRequestDTO.getCookies(), is(empty()));
        assertThat(httpRequestDTO.getHeaders(), is(empty()));
        assertThat(httpRequestDTO.getMethod(), is(""));
        assertThat(httpRequestDTO.getPath(), is(""));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(empty()));
    }
}
