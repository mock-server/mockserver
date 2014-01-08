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

    /**
     * The status code to return, such as 200, 404, the status code specified
     * here will result in the default status message for this status code for
     * example for 200 the status message "OK" is used
     *
     * @param statusCode an integer such as 200 or 404
     */
    public HttpResponse withStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * The response body
     *
     * @param body a UTF-8 string
     */
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

    /**
     * The cookies to return as Set-Cookie headers as a list of Cookie objects
     *
     * @param cookies a list of Cookie objects
     */
    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }
    /**
     * The cookies to return as Set-Cookie headers as a varargs of Cookie objects
     *
     * @param cookies a varargs of Cookie objects
     */
    public HttpResponse withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    /**
     * The headers to return as a list of Header objects
     *
     * @param headers a list of Header objects
     */
    public HttpResponse withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }
    /**
     * The headers to return as a varargs of Header objects
     *
     * @param headers a varargs of Header objects
     */
    public HttpResponse withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
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

