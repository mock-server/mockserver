package org.mockserver.templates.engine.model;

import org.junit.Test;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

public class HttpResponseTemplateObjectTest {

    @Test
    public void shouldBuildFromFullHttpResponse() {
        HttpResponse response = HttpResponse.response()
            .withStatusCode(200)
            .withReasonPhrase("OK")
            .withHeader("Content-Type", "application/json")
            .withHeader("Accept", "text/html", "application/xml")
            .withCookie("session", "abc123")
            .withBody("some_body");

        HttpResponseTemplateObject templateObject = new HttpResponseTemplateObject(response);

        assertThat(templateObject.getStatusCode(), is(200));
        assertThat(templateObject.getReasonPhrase(), is("OK"));
        assertThat(templateObject.getHeaders().get("Content-Type"), is(Arrays.asList("application/json")));
        assertThat(templateObject.getHeaders().get("Accept"), is(Arrays.asList("text/html", "application/xml")));
        assertThat(templateObject.getCookies().get("session"), is("abc123"));
        assertThat(templateObject.getBody(), is("some_body"));
    }

    @Test
    public void shouldHandleNullHttpResponse() {
        HttpResponseTemplateObject templateObject = new HttpResponseTemplateObject(null);

        assertThat(templateObject.getStatusCode(), is(nullValue()));
        assertThat(templateObject.getReasonPhrase(), is(nullValue()));
        assertThat(templateObject.getHeaders(), is(anEmptyMap()));
        assertThat(templateObject.getCookies(), is(anEmptyMap()));
        assertThat(templateObject.getBody(), is(""));
    }

    @Test
    public void shouldReturnValidJsonFromToString() {
        HttpResponse response = HttpResponse.response()
            .withStatusCode(200)
            .withReasonPhrase("OK")
            .withBody("test_body");

        HttpResponseTemplateObject templateObject = new HttpResponseTemplateObject(response);

        String jsonString = templateObject.toString();
        assertThat(jsonString, containsString("statusCode"));
        assertThat(jsonString, containsString("200"));
    }

}
