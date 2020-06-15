package org.mockserver.mock.action.http;

import org.mockserver.client.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler extends HttpForwardAction {

    public HttpForwardActionHandler(MockServerLogger logFormatter, NettyHttpClient httpClient) {
        super(logFormatter, httpClient);
    }

    public HttpForwardActionResult handle(HttpForward httpForward, HttpRequest httpRequest) {
        if (httpForward.getScheme().equals(HttpForward.Scheme.HTTPS)) {
            httpRequest.withSecure(true);
        } else {
            httpRequest.withSecure(false);
        }
        return sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()), null);
    }

}
