package org.mockserver.mock.action.http;

import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public abstract class HttpForwardAction {

    protected final MockServerLogger mockServerLogger;
    private final NettyHttpClient httpClient;
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

    HttpForwardAction(MockServerLogger mockServerLogger, NettyHttpClient httpClient) {
        this.mockServerLogger = mockServerLogger;
        this.httpClient = httpClient;
    }

    protected HttpForwardActionResult sendRequest(HttpRequest request, @Nullable InetSocketAddress remoteAddress, Function<HttpResponse, HttpResponse> overrideHttpResponse) {
        try {
            return new HttpForwardActionResult(request, httpClient.sendRequest(hopByHopHeaderFilter.onRequest(request), remoteAddress), overrideHttpResponse, remoteAddress);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception forwarding request " + request)
                    .setThrowable(e)
            );
        }
        return notFoundFuture(request);
    }

    HttpForwardActionResult notFoundFuture(HttpRequest httpRequest) {
        CompletableFuture<HttpResponse> notFoundFuture = new CompletableFuture<>();
        notFoundFuture.complete(notFoundResponse());
        return new HttpForwardActionResult(httpRequest, notFoundFuture, null);
    }
}
