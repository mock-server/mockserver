package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class LogEventRequestAndResponse extends ObjectWithJsonToString {

    private String timestamp;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    public String getTimestamp() {
        return timestamp;
    }

    public LogEventRequestAndResponse withTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public LogEventRequestAndResponse withHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public LogEventRequestAndResponse withHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }
}
