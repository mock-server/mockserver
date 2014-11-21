package org.mockserver.mock.action;

import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.Header.header;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();

    public HttpResponse handle(HttpForward httpForward, HttpRequest httpRequest) {
        if (httpForward.getScheme().equals(HttpForward.Scheme.HTTPS)) {
            httpRequest.setSecure(true);
        } else {
            httpRequest.setSecure(false);
        }
        return sendRequest(outboundRequest(httpForward.getHost(), httpForward.getPort(), "", httpRequest));
    }

    private HttpResponse sendRequest(OutboundHttpRequest httpRequest) {
        if (httpRequest != null) {
            try {
                return httpClient.sendRequest(httpRequest);
            } catch (Exception e) {
                logger.error("Exception forwarding request " + httpRequest, e);
            }
        }
        return null;
    }
}
