package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockserver.model.HttpResponse.response;

public class HttpForwardActionResult {
    private final HttpRequest httpRequest;
    private final CompletableFuture<HttpResponse> httpResponse;
    private final Function<HttpResponse, HttpResponse> overrideHttpResponse;
    private final InetSocketAddress remoteAddress;

    HttpForwardActionResult(HttpRequest httpRequest, CompletableFuture<HttpResponse> httpResponse, Function<HttpResponse, HttpResponse> overrideHttpResponse) {
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
        } else {
            return httpResponse.thenApply(response -> {
                if (response != null) {
                    return overrideHttpResponse.apply(response);
                } else {
                    return null;
                }
            });
        }
    }

    Function<HttpResponse, HttpResponse> getOverrideHttpResponse() {
        return overrideHttpResponse;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}