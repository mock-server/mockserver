package org.jamesdbloom.mockserver.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends ModelObject {
    private String path = "";
    private String body = "";
    private List<Header> headers = new ArrayList<Header>();
    private List<Parameter> queryParameters = new ArrayList<Parameter>();
    private List<Parameter> bodyParameters = new ArrayList<Parameter>();
    private List<Cookie> cookies = new ArrayList<Cookie>();

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
        return headers;
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
        return cookies;
    }

    public HttpRequest withQueryParameters(Parameter... queryParameters) {
        this.queryParameters = Arrays.asList(queryParameters);
        return this;
    }

    public List<Parameter> getQueryParameters() {
        return queryParameters;
    }

    public HttpRequest withBodyParameters(Parameter... bodyParameters) {
        this.bodyParameters = Arrays.asList(bodyParameters);
        return this;
    }

    public List<Parameter> getBodyParameters() {
        return bodyParameters;
    }
}
