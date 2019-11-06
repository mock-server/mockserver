package org.mockserver.serialization.model;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.model.*;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        Cookies cookies = new Cookies().withEntries(cookie("name", "value"));
        Headers headers = new Headers().withEntries(header("name", "value"));
        String method = "METHOD";
        String path = "path";
        Parameters queryStringParameters = new Parameters().withEntries(param("name", "value"));
        HttpRequest httpRequest = new HttpRequest()
                .withBody("body")
                .withCookies(new Cookie("name", "value"))
                .withHeaders(new Header("name", "value"))
                .withMethod(method)
                .withPath(path)
                .withQueryStringParameter(new Parameter("name", "value"))
                .withKeepAlive(true)
                .withSecure(true);

        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(httpRequest);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getMethod(), is(string(method)));
        assertThat(httpRequestDTO.getPath(), is(string(path)));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(queryStringParameters));
        assertThat(httpRequestDTO.getKeepAlive(), is(Boolean.TRUE));
        assertThat(httpRequestDTO.getSecure(), is(Boolean.TRUE));
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
                .withQueryStringParameter(parameter)
                .withKeepAlive(true)
                .withSecure(true);

        // when
        HttpRequest builtHttpRequest = new HttpRequestDTO(httpRequest).buildObject();

        // then
        assertThat(builtHttpRequest.getBody(), Is.<org.mockserver.model.Body>is(exact(body)));
        assertThat(builtHttpRequest.getCookieList(), containsInAnyOrder(cookie));
        assertThat(builtHttpRequest.getHeaderList(), containsInAnyOrder(header));
        assertThat(builtHttpRequest.getMethod(), is(string(method)));
        assertThat(builtHttpRequest.getPath(), is(string(path)));
        assertThat(builtHttpRequest.getQueryStringParameterList(), containsInAnyOrder(parameter));
        assertThat(builtHttpRequest.isKeepAlive(), is(Boolean.TRUE));
        assertThat(builtHttpRequest.isSecure(), is(Boolean.TRUE));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        Cookies cookies = new Cookies().withEntries(cookie("name", "value"));
        Headers headers = new Headers().withEntries(header("name", "value"));
        String method = "METHOD";
        String path = "path";
        Parameters queryStringParameters = new Parameters().withEntries(param("name", "value"));
        HttpRequest httpRequest = new HttpRequest();

        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(httpRequest);
        httpRequestDTO.setBody(body);
        httpRequestDTO.setCookies(cookies);
        httpRequestDTO.setHeaders(headers);
        httpRequestDTO.setMethod(string(method));
        httpRequestDTO.setPath(string(path));
        httpRequestDTO.setQueryStringParameters(queryStringParameters);
        httpRequestDTO.setKeepAlive(Boolean.TRUE);
        httpRequestDTO.setSecure(Boolean.TRUE);

        // then
        assertThat(httpRequestDTO.getBody(), is(body));
        assertThat(httpRequestDTO.getCookies(), is(cookies));
        assertThat(httpRequestDTO.getHeaders(), is(headers));
        assertThat(httpRequestDTO.getMethod(), is(string(method)));
        assertThat(httpRequestDTO.getPath(), is(string(path)));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(queryStringParameters));
        assertThat(httpRequestDTO.getKeepAlive(), is(Boolean.TRUE));
        assertThat(httpRequestDTO.getSecure(), is(Boolean.TRUE));
    }


    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(null);

        // then
        assertThat(httpRequestDTO.getBody(), is(nullValue()));
        assertThat(httpRequestDTO.getCookies(), is(nullValue()));
        assertThat(httpRequestDTO.getHeaders(), is(nullValue()));
        assertThat(httpRequestDTO.getMethod(), is(string("")));
        assertThat(httpRequestDTO.getPath(), is(string("")));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(nullValue()));
        assertThat(httpRequestDTO.getKeepAlive(), is(nullValue()));
        assertThat(httpRequestDTO.getSecure(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpRequestDTO httpRequestDTO = new HttpRequestDTO(new HttpRequest());

        // then
        assertThat(httpRequestDTO.getBody(), is(nullValue()));
        assertThat(httpRequestDTO.getCookies(), is(nullValue()));
        assertThat(httpRequestDTO.getHeaders(), is(nullValue()));
        assertThat(httpRequestDTO.getMethod(), is(string("")));
        assertThat(httpRequestDTO.getPath(), is(string("")));
        assertThat(httpRequestDTO.getQueryStringParameters(), is(nullValue()));
        assertThat(httpRequestDTO.getKeepAlive(), is(nullValue()));
        assertThat(httpRequestDTO.getSecure(), is(nullValue()));
    }
}
