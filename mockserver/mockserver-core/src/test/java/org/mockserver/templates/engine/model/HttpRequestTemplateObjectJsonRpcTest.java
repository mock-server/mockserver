package org.mockserver.templates.engine.model;

import org.junit.Test;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

public class HttpRequestTemplateObjectJsonRpcTest {

    @Test
    public void shouldExtractJsonRpcFieldsFromValidRequest() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is("1"));
        assertThat(templateObj.getJsonRpcMethod(), is("tools/list"));
    }

    @Test
    public void shouldReturnNullWhenBodyIsNotJsonRpc() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/api")
            .withBody("{\"name\": \"value\"}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is(nullValue()));
    }

    @Test
    public void shouldReturnNullWhenBodyIsNull() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("GET")
            .withPath("/api");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is(nullValue()));
    }

    @Test
    public void shouldReturnNumericIdAsString() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"id\": 42}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is("42"));
        assertThat(templateObj.getJsonRpcMethod(), is("tools/call"));
    }

    @Test
    public void shouldReturnStringIdAsString() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"resources/read\", \"id\": \"abc\"}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is("abc"));
        assertThat(templateObj.getJsonRpcMethod(), is("resources/read"));
    }

    @Test
    public void shouldReturnNullIdWhenIdIsMissing() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"notifications/send\"}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is("notifications/send"));
    }

    @Test
    public void shouldReturnNullStringWhenIdIsJsonNull() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": null}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is("null"));
        assertThat(templateObj.getJsonRpcMethod(), is("tools/list"));
    }

    @Test
    public void shouldReturnNullWhenBodyIsNotJson() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("this is not json");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is(nullValue()));
    }

    @Test
    public void shouldReturnNullWhenBodyHasJsonRpcButNoMethod() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"id\": 1}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is(nullValue()));
    }

    @Test
    public void shouldReturnNullWhenBodyIsEmptyString() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is(nullValue()));
        assertThat(templateObj.getJsonRpcMethod(), is(nullValue()));
    }

    @Test
    public void shouldExtractJsonRpcFieldsWithParams() {
        // given
        HttpRequest request = HttpRequest.request()
            .withMethod("POST")
            .withPath("/rpc")
            .withBody("{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"id\": 5, \"params\": {\"name\": \"calculator\"}}");

        // when
        HttpRequestTemplateObject templateObj = new HttpRequestTemplateObject(request);

        // then
        assertThat(templateObj.getJsonRpcId(), is("5"));
        assertThat(templateObj.getJsonRpcMethod(), is("tools/call"));
    }
}
