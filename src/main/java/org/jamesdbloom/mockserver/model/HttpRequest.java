package org.jamesdbloom.mockserver.model;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends ModelObject {
    private String path = null;
    private String body = null;
    private List<Header> headers = null;
    private List<Parameter> queryParameters = null;
    private List<Parameter> bodyParameters = null;
    private List<Cookie> cookies;

    public String getPath() {
        return path;
    }

    public HttpRequest withPath(String path) {
        this.path = path;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpRequest withBody(String body) {
        this.body = body;
        return this;
    }

    public HttpRequest withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    public HttpRequest withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        if (headers == null) {
            return null;
        } else {
            return headers;
        }
    }

    public HttpRequest withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public HttpRequest withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        if (cookies == null) {
            return null;
        } else {
            return cookies;
        }
    }

    public HttpRequest withQueryParameters(Parameter... queryParameters) {
        this.queryParameters = Arrays.asList(queryParameters);
        return this;
    }

    public List<Parameter> getQueryParameters() {
        if (queryParameters == null) {
            return null;
        } else {
            return queryParameters;
        }
    }

    public HttpRequest withBodyParameters(Parameter... bodyParameters) {
        this.bodyParameters = Arrays.asList(bodyParameters);
        return this;
    }

    public List<Parameter> getBodyParameters() {
        if (bodyParameters == null) {
            return null;
        } else {
            return bodyParameters;
        }
    }
}
