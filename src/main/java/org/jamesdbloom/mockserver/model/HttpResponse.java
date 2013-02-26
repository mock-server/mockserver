package org.jamesdbloom.mockserver.model;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends ModelObject {
    private Integer responseCode;
    private String body;
    private Cookie[] cookies;
    private Header[] headers;
    private Delay delay;

    public HttpResponse withStatusCode(Integer responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public HttpResponse withBody(String body) {
        this.body = body;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpResponse withCookies(Cookie... cookies) {
        this.cookies = cookies;
        return this;
    }

    public Cookie[] getCookies() {
        return cookies;
    }

    public HttpResponse withHeaders(Header... headers) {
        this.headers = headers;
        return this;
    }

    public Header[] getHeaders() {
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

