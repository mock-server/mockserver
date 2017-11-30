package org.mockserver.mock.action;

import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private NettyHttpClient httpClient = new NettyHttpClient();

    public HttpResponse handle(HttpForward httpForward, HttpRequest httpRequest) {
        if (httpForward.getScheme().equals(HttpForward.Scheme.HTTPS)) {
            httpRequest.withSecure(true);
        } else {
            httpRequest.withSecure(false);
        }
        return sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    private HttpResponse sendRequest(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
        if (httpRequest != null) {
            try {
                return httpClient.sendRequest(httpRequest, remoteAddress);
            } catch (Exception e) {
                logger.error("Exception forwarding request " + httpRequest, e);
            }
        }
        return null;
    }
}
