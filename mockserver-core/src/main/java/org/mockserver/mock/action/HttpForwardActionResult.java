package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

public class HttpForwardActionResult {
    private final HttpRequest httpRequest;
    private final SettableFuture<HttpResponse> httpResponse;
    private final InetSocketAddress remoteAddress;

    HttpForwardActionResult(HttpRequest httpRequest, SettableFuture<HttpResponse> httpResponse) {
        this(httpRequest, httpResponse, null);
    }

    HttpForwardActionResult(HttpRequest httpRequest, SettableFuture<HttpResponse> httpResponse, InetSocketAddress remoteAddress) {
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
        this.remoteAddress = remoteAddress;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public SettableFuture<HttpResponse> getHttpResponse() {
        return httpResponse;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}