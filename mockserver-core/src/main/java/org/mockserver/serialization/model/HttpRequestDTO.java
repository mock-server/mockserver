package org.mockserver.serialization.model;

import org.mockserver.model.*;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTO extends NotDTO implements DTO<HttpRequest> {
    private NottableString method = string("");
    private NottableString path = string("");
    private Parameters queryStringParameters;
    private BodyDTO body;
    private Cookies cookies;
    private Headers headers;
    private Boolean keepAlive = null;
    private Boolean secure = null;

    public HttpRequestDTO(HttpRequest httpRequest) {
        this(httpRequest, false);
    }

    public HttpRequestDTO() {
        super(false);
    }

    public HttpRequestDTO(HttpRequest httpRequest, Boolean not) {
        super(not);
        if (httpRequest != null) {
            method = httpRequest.getMethod();
            path = httpRequest.getPath();
            headers = httpRequest.getHeaders();
            cookies = httpRequest.getCookies();
            queryStringParameters = httpRequest.getQueryStringParameters();
            body = BodyDTO.createDTO(httpRequest.getBody());
            keepAlive = httpRequest.isKeepAlive();
            secure = httpRequest.isSecure();
        }
    }

    public HttpRequest buildObject() {
        return new HttpRequest()
            .withMethod(method)
            .withPath(path)
            .withQueryStringParameters(queryStringParameters)
            .withBody((body != null ? Not.not(body.buildObject(), body.getNot()) : null))
            .withHeaders(headers)
            .withCookies(cookies)
            .withSecure(secure)
            .withKeepAlive(keepAlive);
    }

    public NottableString getMethod() {
        return method;
    }

    public HttpRequestDTO setMethod(NottableString method) {
        this.method = method;
        return this;
    }

    public NottableString getPath() {
        return path;
    }

    public HttpRequestDTO setPath(NottableString path) {
        this.path = path;
        return this;
    }

    public Parameters getQueryStringParameters() {
        return queryStringParameters;
    }

    public HttpRequestDTO setQueryStringParameters(Parameters queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
        return this;
    }

    public BodyDTO getBody() {
        return body;
    }

    public HttpRequestDTO setBody(BodyDTO body) {
        this.body = body;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public HttpRequestDTO setHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    public Cookies getCookies() {
        return cookies;
    }

    public HttpRequestDTO setCookies(Cookies cookies) {
        this.cookies = cookies;
        return this;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public HttpRequestDTO setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public Boolean getSecure() {
        return secure;
    }

    public HttpRequestDTO setSecure(Boolean secure) {
        this.secure = secure;
        return this;
    }
}
