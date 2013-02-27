package org.jamesdbloom.mockserver.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.client.serialization.model.CookieDTO;
import org.jamesdbloom.mockserver.client.serialization.model.HeaderDTO;
import org.jamesdbloom.mockserver.client.serialization.model.HttpRequestDTO;
import org.jamesdbloom.mockserver.client.serialization.model.ParameterDTO;

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

    public HttpRequest(HttpRequestDTO httpRequest) {
        path = httpRequest.getPath();
        body = httpRequest.getBody();
        headers = Lists.transform(httpRequest.getHeaders(), new Function<HeaderDTO, Header>() {
            public Header apply(HeaderDTO header) {
                return new Header(header);
            }
        });
        cookies = Lists.transform(httpRequest.getCookies(), new Function<CookieDTO, Cookie>() {
            public Cookie apply(CookieDTO cookie) {
                return new Cookie(cookie);
            }
        });
        queryParameters = Lists.transform(httpRequest.getQueryParameters(), new Function<ParameterDTO, Parameter>() {
            public Parameter apply(ParameterDTO parameter) {
                return new Parameter(parameter);
            }
        });
        bodyParameters = Lists.transform(httpRequest.getBodyParameters(), new Function<ParameterDTO, Parameter>() {
            public Parameter apply(ParameterDTO parameter) {
                return new Parameter(parameter);
            }
        });
    }

    public HttpRequest() { }

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
