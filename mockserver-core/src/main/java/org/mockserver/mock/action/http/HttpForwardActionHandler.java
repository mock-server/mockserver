package org.mockserver.mock.action.http;

import org.mockserver.httpclient.NettyHttpClient;
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
        httpRequest.withSecure(HttpForward.Scheme.HTTPS.equals(httpForward.getScheme()));
        return sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()), null);
    }

}
