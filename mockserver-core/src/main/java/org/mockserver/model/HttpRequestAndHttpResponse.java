package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponse extends ObjectWithJsonToString {

    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpRequestAndHttpResponse withHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpRequestAndHttpResponse withHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }
}
