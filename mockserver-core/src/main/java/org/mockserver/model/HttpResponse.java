package org.mockserver.model;

import org.apache.commons.io.Charsets;
import org.mockserver.client.serialization.ObjectMapperFactory;

import java.util.*;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends EqualsHashCodeToString {
    private Integer statusCode = 200;
    private byte[] body = new byte[0];
    private Map<String, Header> headers = new LinkedHashMap<String, Header>();
    private Map<String, Cookie> cookies = new LinkedHashMap<String, Cookie>();
    private Delay delay;

    public HttpResponse() {
    }

    public static HttpResponse response() {
        return new HttpResponse();
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

    public int getContentLength() {
        return body.length;
    }

    public String getBodyAsString() {
        return new String(body, Charsets.UTF_8);
    }

    /**
     * The headers to return as a list of Header objects
     *
     * @param headers a list of Header objects
     */
    public HttpResponse withHeaders(List<Header> headers) {
        this.headers.clear();
        for (Header header : headers) {
            withHeader(header);
        }
        return this;
    }

    /**
     * The headers to return as a varargs of Header objects
     *
     * @param headers a varargs of Header objects
     */
    public HttpResponse withHeaders(Header... headers) {
        withHeaders(Arrays.asList(headers));
        return this;
    }

    /**
     * A header to return as a Header objects
     *
     * @param header a Header objects
     */
    public HttpResponse withHeader(Header header) {
        if (this.headers.containsKey(header.getName())) {
            this.headers.get(header.getName()).addValues(header.getValues());
        } else {
            this.headers.put(header.getName(), header);
        }
        return this;
    }

    public List<Header> getHeaders() {
        return new ArrayList<Header>(headers.values());
    }

    /**
     * The cookies to return as Set-Cookie headers as a list of Cookie objects
     *
     * @param cookies a list of Cookie objects
     */
    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies.clear();
        for (Cookie cookie : cookies) {
            withCookie(cookie);
        }
        return this;
    }

    /**
     * The cookies to return as Set-Cookie headers as a varargs of Cookie objects
     *
     * @param cookies a varargs of Cookie objects
     */
    public HttpResponse withCookies(Cookie... cookies) {
        withCookies(Arrays.asList(cookies));
        return this;
    }

    /**
     * Add cookie to return as Set-Cookie header
     *
     * @param cookie a Cookie object
     */
    public HttpResponse withCookie(Cookie cookie) {
        if (this.cookies.containsKey(cookie.getName())) {
            this.cookies.get(cookie.getName()).addValues(cookie.getValues());
        } else {
            this.cookies.put(cookie.getName(), cookie);
        }
        return this;
    }

    public List<Cookie> getCookies() {
        return new ArrayList<Cookie>(cookies.values());
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

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}

