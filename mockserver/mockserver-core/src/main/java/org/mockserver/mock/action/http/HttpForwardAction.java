package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.SocketAddress;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public abstract class HttpForwardAction {

    protected final MockServerLogger mockServerLogger;
    protected final Configuration configuration;
    private final NettyHttpClient httpClient;
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

    HttpForwardAction(MockServerLogger mockServerLogger, Configuration configuration, NettyHttpClient httpClient) {
        this.mockServerLogger = mockServerLogger;
        this.configuration = configuration;
        this.httpClient = httpClient;
    }

    protected HttpForwardActionResult sendRequest(HttpRequest request, @Nullable InetSocketAddress remoteAddress, Function<HttpResponse, HttpResponse> overrideHttpResponse) {
        try {
            // TODO(jamesdbloom) support proxying via HTTP2, for now always force into HTTP1
            return new HttpForwardActionResult(request, httpClient.sendRequest(hopByHopHeaderFilter.onRequest(request).withProtocol(null), remoteAddress), overrideHttpResponse, remoteAddress);
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

    protected void adjustHostHeader(HttpRequest request) {
        if (configuration != null && configuration.forwardAdjustHostHeader()) {
            SocketAddress sa = request.getSocketAddress();
            if (sa != null && isNotBlank(sa.getHost())) {
                boolean defaultPort = (SocketAddress.Scheme.HTTPS.equals(sa.getScheme()) && sa.getPort() != null && sa.getPort() == 443)
                    || (SocketAddress.Scheme.HTTP.equals(sa.getScheme()) && sa.getPort() != null && sa.getPort() == 80)
                    || (sa.getPort() == null);
                String hostHeader = defaultPort ? sa.getHost() : sa.getHost() + ":" + sa.getPort();
                request.replaceHeader(new Header("Host", hostHeader));
            }
        }
    }

    HttpForwardActionResult notFoundFuture(HttpRequest httpRequest) {
        CompletableFuture<HttpResponse> notFoundFuture = new CompletableFuture<>();
        notFoundFuture.complete(notFoundResponse());
        return new HttpForwardActionResult(httpRequest, notFoundFuture, null);
    }
}
