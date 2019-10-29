package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponse extends ObjectWithJsonToString {

    private String timestamp;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    public String getTimestamp() {
        return timestamp;
    }

    public HttpRequestAndHttpResponse setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpRequestAndHttpResponse setHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpRequestAndHttpResponse setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }
}
