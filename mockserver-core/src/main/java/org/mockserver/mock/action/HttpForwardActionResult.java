package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class HttpForwardActionResult {
    private final HttpRequest httpRequest;
    private final InetSocketAddress remoteAddress;
    private CompletableFuture<HttpResponse> httpResponse;
    private final Function<HttpResponse, HttpResponse> overrideHttpResponse;
    private final AtomicBoolean overrideHttpResponseApplied = new AtomicBoolean(false);

    public HttpForwardActionResult(HttpRequest httpRequest, CompletableFuture<HttpResponse> httpResponse, Function<HttpResponse, HttpResponse> overrideHttpResponse) {
        this(httpRequest, httpResponse, overrideHttpResponse, null);
    }

    HttpForwardActionResult(HttpRequest httpRequest, CompletableFuture<HttpResponse> httpResponse, Function<HttpResponse, HttpResponse> overrideHttpResponse, InetSocketAddress remoteAddress) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.overrideHttpResponse = overrideHttpResponse;
        this.remoteAddress = remoteAddress;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public CompletableFuture<HttpResponse> getHttpResponse() {
        if (overrideHttpResponse == null) {
            return httpResponse;
        }
        if (overrideHttpResponseApplied.compareAndSet(false, true)) {
            httpResponse = httpResponse.thenApply(response -> {
                if (response != null) {
                    return overrideHttpResponse.apply(response);
                } else {
                    return null;
                }
            });
        }
        return httpResponse;
    }

    public HttpForwardActionResult setHttpResponse(CompletableFuture<HttpResponse> httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}