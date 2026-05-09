package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;

import java.net.InetSocketAddress;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler extends HttpForwardAction {

    public HttpForwardActionHandler(MockServerLogger logFormatter, Configuration configuration, NettyHttpClient httpClient) {
        super(logFormatter, configuration, httpClient);
    }

    public HttpForwardActionResult handle(HttpForward httpForward, HttpRequest httpRequest) {
        httpRequest.withSecure(HttpForward.Scheme.HTTPS.equals(httpForward.getScheme()));
        int port = httpForward.getPort();
        boolean defaultPort = (HttpForward.Scheme.HTTPS.equals(httpForward.getScheme()) && port == 443)
            || (HttpForward.Scheme.HTTP.equals(httpForward.getScheme()) && port == 80);
        String hostHeader = defaultPort ? httpForward.getHost() : httpForward.getHost() + ":" + port;
        httpRequest.replaceHeader(new Header("Host", hostHeader));
        return sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()), null);
    }

}
