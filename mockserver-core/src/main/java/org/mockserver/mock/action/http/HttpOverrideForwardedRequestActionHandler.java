package org.mockserver.mock.action.http;

import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestActionHandler extends HttpForwardAction {

    public HttpOverrideForwardedRequestActionHandler(MockServerLogger logFormatter, NettyHttpClient httpClient) {
        super(logFormatter, httpClient);
    }

    public HttpForwardActionResult handle(final HttpOverrideForwardedRequest httpOverrideForwardedRequest, final HttpRequest request) {
        return sendRequest(request.clone().update(httpOverrideForwardedRequest.getRequestOverride()), null, httpResponse -> {
            if (httpResponse == null) {
                return httpOverrideForwardedRequest.getResponseOverride();
            } else if (httpOverrideForwardedRequest.getResponseOverride() == null) {
                return httpResponse;
            } else {
                return httpResponse.update(httpOverrideForwardedRequest.getResponseOverride());
            }
        });
    }

}
