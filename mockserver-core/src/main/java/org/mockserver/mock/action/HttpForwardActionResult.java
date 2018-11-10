package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class HttpForwardActionResult {
    private final HttpRequest httpRequest;
    private final SettableFuture<HttpResponse> httpResponse;

    HttpForwardActionResult(HttpRequest httpRequest, SettableFuture<HttpResponse> httpResponse) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public SettableFuture<HttpResponse> getHttpResponse() {
        return httpResponse;
    }
}