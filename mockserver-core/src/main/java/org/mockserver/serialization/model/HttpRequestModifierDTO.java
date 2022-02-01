package org.mockserver.serialization.model;

import org.mockserver.model.HttpRequestModifier;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.PathModifier;

public class HttpRequestModifierDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpRequestModifier> {

    private PathModifier path;
    private QueryParametersModifierDTO queryStringParameters;
    private HeadersModifierDTO headers;
    private CookiesModifierDTO cookies;

    public HttpRequestModifierDTO() {
    }

    public HttpRequestModifierDTO(HttpRequestModifier httpRequestModifier) {
        if (httpRequestModifier != null) {
            path = httpRequestModifier.getPath();
            queryStringParameters = httpRequestModifier.getQueryStringParameters() != null ? new QueryParametersModifierDTO(httpRequestModifier.getQueryStringParameters()) : null;
            headers = httpRequestModifier.getHeaders() != null ? new HeadersModifierDTO(httpRequestModifier.getHeaders()) : null;
            cookies = httpRequestModifier.getCookies() != null ? new CookiesModifierDTO(httpRequestModifier.getCookies()) : null;
        }
    }

    public HttpRequestModifier buildObject() {
        return new HttpRequestModifier()
            .withPath(path)
            .withQueryStringParameters(queryStringParameters != null ? queryStringParameters.buildObject() : null)
            .withHeaders(headers != null ? headers.buildObject() : null)
            .withCookies(cookies != null ? cookies.buildObject() : null);
    }

    public PathModifier getPath() {
        return path;
    }

    public HttpRequestModifierDTO setPath(PathModifier path) {
        this.path = path;
        return this;
    }

    public QueryParametersModifierDTO getQueryStringParameters() {
        return queryStringParameters;
    }

    public HttpRequestModifierDTO setQueryStringParameters(QueryParametersModifierDTO queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
        return this;
    }

    public HeadersModifierDTO getHeaders() {
        return headers;
    }

    public HttpRequestModifierDTO setHeaders(HeadersModifierDTO headers) {
        this.headers = headers;
        return this;
    }

    public CookiesModifierDTO getCookies() {
        return cookies;
    }

    public HttpRequestModifierDTO setCookies(CookiesModifierDTO cookies) {
        this.cookies = cookies;
        return this;
    }
}
