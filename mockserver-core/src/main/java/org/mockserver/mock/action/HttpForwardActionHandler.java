package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler {
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

    public SettableFuture<HttpResponse> handle(HttpForward httpForward, HttpRequest httpRequest) {
        if (httpForward.getScheme().equals(HttpForward.Scheme.HTTPS)) {
            httpRequest.withSecure(true);
        } else {
            httpRequest.withSecure(false);
        }
        return httpClient.sendRequest(
            hopByHopHeaderFilter.onRequest(httpRequest),
            new InetSocketAddress(httpForward.getHost(), httpForward.getPort())
        );
    }

}
