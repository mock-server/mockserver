package org.mockserver.model;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponse extends ObjectWithJsonToString {
    private int hashCode;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpRequestAndHttpResponse withHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        this.hashCode = 0;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpRequestAndHttpResponse withHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        this.hashCode = 0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        HttpRequestAndHttpResponse that = (HttpRequestAndHttpResponse) o;
        return Objects.equals(httpRequest, that.httpRequest) &&
            Objects.equals(httpResponse, that.httpResponse);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(httpRequest, httpResponse);
        }
        return hashCode;
    }
}
