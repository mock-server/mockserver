package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestActionHandler extends HttpForwardAction {

    public HttpOverrideForwardedRequestActionHandler(MockServerLogger logFormatter, Configuration configuration, NettyHttpClient httpClient) {
        super(logFormatter, configuration, httpClient);
    }

    public HttpForwardActionResult handle(final HttpOverrideForwardedRequest httpOverrideForwardedRequest, final HttpRequest request) {
        if (httpOverrideForwardedRequest != null) {
            HttpRequest requestToSend = request.clone().update(httpOverrideForwardedRequest.getRequestOverride(), httpOverrideForwardedRequest.getRequestModifier());
            boolean hasExplicitHostOverride = httpOverrideForwardedRequest.getRequestOverride() != null
                && httpOverrideForwardedRequest.getRequestOverride().containsHeader("Host");
            if (!hasExplicitHostOverride) {
                adjustHostHeader(requestToSend);
            }
            return sendRequest(requestToSend, null, httpResponse -> {
                if (httpResponse == null) {
                    return httpOverrideForwardedRequest.getResponseOverride();
                } else {
                    return httpResponse.update(httpOverrideForwardedRequest.getResponseOverride(), httpOverrideForwardedRequest.getResponseModifier());
                }
            });
        } else {
            return sendRequest(request, null, httpResponse -> httpResponse);
        }
    }

}
