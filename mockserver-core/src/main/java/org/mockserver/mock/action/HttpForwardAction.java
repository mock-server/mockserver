package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public abstract class HttpForwardAction {

    private final LoggingFormatter logFormatter;
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

    HttpForwardAction(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
    }

    protected SettableFuture<HttpResponse> sendRequest(HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) {
        try {
            return httpClient.sendRequest(hopByHopHeaderFilter.onRequest(httpRequest), remoteAddress);
        } catch (Exception e) {
            logFormatter.errorLog(httpRequest, e, "Exception forwarding request " + httpRequest);
        }
        return notFoundFuture();
    }

    SettableFuture<HttpResponse> notFoundFuture() {
        SettableFuture<HttpResponse> notFoundFuture = SettableFuture.create();
        notFoundFuture.set(notFoundResponse());
        return notFoundFuture;
    }
}
