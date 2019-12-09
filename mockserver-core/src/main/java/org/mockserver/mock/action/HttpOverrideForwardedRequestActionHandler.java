package org.mockserver.mock.action;

import org.mockserver.client.NettyHttpClient;
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
        return sendRequest(request.clone().update(httpOverrideForwardedRequest.getHttpRequest()), null, httpResponse -> {
            if (httpResponse == null) {
                return httpOverrideForwardedRequest.getHttpResponse();
            } else if (httpOverrideForwardedRequest.getHttpResponse() == null) {
                return httpResponse;
            } else {
                return httpResponse.update(httpOverrideForwardedRequest.getHttpResponse());
            }
        });
    }

}
