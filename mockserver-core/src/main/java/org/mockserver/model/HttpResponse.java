package org.mockserver.model;

import org.apache.commons.lang3.CharEncoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends EqualsHashCodeToString {
    private Integer statusCode = 200;
    private byte[] body = new byte[0];
    private List<Cookie> cookies = new ArrayList<Cookie>();
    private List<Header> headers = new ArrayList<Header>();
    private Delay delay;

    public static HttpResponse response() {
        return new HttpResponse();
    }

    public HttpResponse() {
    }

    public HttpResponse withStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public HttpResponse withBody(String body) {
        if (body != null) {
            this.body = body.getBytes();
        } else {
            this.body = new byte[0];
        }
        return this;
    }

    public HttpResponse withBody(byte[] body) {
        this.body = body;
        return this;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public HttpResponse withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public HttpResponse withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    public HttpResponse withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public HttpResponse withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    public HttpResponse applyDelay() {
        if (delay != null) {
            delay.applyDelay();
        }
        return this;
    }
}

