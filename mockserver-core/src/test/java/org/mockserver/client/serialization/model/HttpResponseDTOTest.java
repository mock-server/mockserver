package org.mockserver.client.serialization.model;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        BodyDTO body = BodyDTO.createDTO(exact("body"));
        Cookies cookies = new Cookies().withEntries(cookie("name", "value"));
        Headers headers = new Headers().withEntries(header("name", "value"));
        Integer statusCode = 200;
        ConnectionOptionsDTO connectionOptions = new ConnectionOptionsDTO().setContentLengthHeaderOverride(50);

        HttpResponse httpResponse = new HttpResponse()
                .withBody("body")
                .withCookies(new Cookie("name", "value"))
                .withHeaders(new Header("name", "value"))
                .withStatusCode(statusCode)
                .withConnectionOptions(new ConnectionOptions().withContentLengthHeaderOverride(50));

        // when
        HttpResponseDTO httpResponseDTO = new HttpResponseDTO(httpResponse);

        // then
        assertThat(httpResponseDTO.getBody(), is(body));
        assertThat(httpResponseDTO.getCookies(), is(cookies));
        assertThat(httpResponseDTO.getHeaders(), is(headers));
        assertThat(httpResponseDTO.getStatusCode(), is(statusCode));
        assertThat(httpResponseDTO.getConnectionOptions(), is(connectionOptions));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String body = "body";
        Cookie cookie = new Cookie("name", "value");
        Header header = new Header("name", "value");
        Integer statusCode = 200;
        ConnectionOptions connectionOptions = new ConnectionOptions().withContentLengthHeaderOverride(50);

        HttpResponse httpResponse = new HttpResponse()
                .withBody(body)
                .withCookies(cookie)
                .withHeaders(header)
                .withStatusCode(statusCode)
                .withConnectionOptions(connectionOptions);

        // when
        HttpResponse builtHttpResponse = new HttpResponseDTO(httpResponse).buildObject();

        // then
        assertThat(builtHttpResponse.getBody(), Is.<org.mockserver.model.Body>is(exact(body)));
        assertThat(builtHttpResponse.getCookieList(), containsInAnyOrder(cookie));
        assertThat(builtHttpResponse.getHeaderList(), containsInAnyOrder(header));
        assertThat(builtHttpResponse.getStatusCode(), is(statusCode));
        assertThat(builtHttpResponse.getConnectionOptions(), is(connectionOptions));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        BodyWithContentTypeDTO body = BodyWithContentTypeDTO.createDTO(exact("body"));
        Cookies cookies = new Cookies().withEntries(cookie("name", "value"));
        Headers headers = new Headers().withEntries(header("name", "value"));
        Integer statusCode = 200;
        ConnectionOptionsDTO connectionOptions = new ConnectionOptionsDTO().setContentLengthHeaderOverride(50);

        HttpResponse httpResponse = new HttpResponse();

        // when
        HttpResponseDTO httpResponseDTO = new HttpResponseDTO(httpResponse);
        httpResponseDTO.setBody(body);
        httpResponseDTO.setCookies(cookies);
        httpResponseDTO.setHeaders(headers);
        httpResponseDTO.setStatusCode(statusCode);
        httpResponseDTO.setConnectionOptions(connectionOptions);

        // then
        assertThat(httpResponseDTO.getBody(), is(body));
        assertThat(httpResponseDTO.getCookies(), is(cookies));
        assertThat(httpResponseDTO.getHeaders(), is(headers));
        assertThat(httpResponseDTO.getStatusCode(), is(statusCode));
        assertThat(httpResponseDTO.getConnectionOptions(), is(connectionOptions));
    }


    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpResponseDTO httpResponseDTO = new HttpResponseDTO(null);

        // then
        assertThat(httpResponseDTO.getBody(), is(nullValue()));
        assertTrue(httpResponseDTO.getCookies().isEmpty());
        assertTrue(httpResponseDTO.getHeaders().isEmpty());
        assertThat(httpResponseDTO.getStatusCode(), is(nullValue()));
        assertThat(httpResponseDTO.getConnectionOptions(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpResponseDTO httpResponseDTO = new HttpResponseDTO(new HttpResponse());

        // then
        assertThat(httpResponseDTO.getBody(), is(nullValue()));
        assertTrue(httpResponseDTO.getCookies().isEmpty());
        assertTrue(httpResponseDTO.getHeaders().isEmpty());
        assertThat(httpResponseDTO.getStatusCode(), is(nullValue()));
        assertThat(httpResponseDTO.getConnectionOptions(), is(nullValue()));
    }
}
