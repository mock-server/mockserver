package org.mockserver.mock.action;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.WARN;

public class HttpForwardActionResult {
    private final HttpRequest httpRequest;
    private final CompletableFuture<HttpResponse> httpResponse;
    private final HttpResponse overriddenHttpResponse;
    private final InetSocketAddress remoteAddress;

    HttpForwardActionResult(HttpRequest httpRequest, CompletableFuture<HttpResponse> httpResponse, HttpResponse overriddenHttpResponse) {
        this(httpRequest, httpResponse, overriddenHttpResponse, null);
    }

    HttpForwardActionResult(HttpRequest httpRequest, CompletableFuture<HttpResponse> httpResponse, HttpResponse overriddenHttpResponse, InetSocketAddress remoteAddress) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.overriddenHttpResponse = overriddenHttpResponse;
        this.remoteAddress = remoteAddress;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public CompletableFuture<HttpResponse> getHttpResponse() {
        if (overriddenHttpResponse == null) {
            return httpResponse;
        } else {
            return httpResponse.thenApply(response -> {
                if (response != null) {
                    return response.update(overriddenHttpResponse);
                } else {
                    return null;
                }
            });
        }
    }

    HttpResponse getOverriddenHttpResponse() {
        return overriddenHttpResponse;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}