package org.mockserver.mock.action;

import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.callback.WebSocketRequestCallback;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import java.util.UUID;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class HttpForwardObjectCallbackActionHandler extends HttpForwardAction {
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpForwardObjectCallbackActionHandler(HttpStateHandler httpStateHandler, NettyHttpClient httpClient) {
        super(httpStateHandler.getMockServerLogger(), httpClient);
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
    }

    public void handle(final ActionHandler actionHandler, final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous) {
        final String clientId = httpObjectCallback.getClientId();
        final String webSocketCorrelationId = UUID.randomUUID().toString();
        webSocketClientRegistry.registerForwardCallbackHandler(webSocketCorrelationId, new WebSocketRequestCallback() {
            @Override
            public void handle(final HttpRequest request) {
                final HttpForwardActionResult responseFuture = sendRequest(request.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), null);
                webSocketClientRegistry.unregisterForwardCallbackHandler(webSocketCorrelationId);
                actionHandler.writeForwardActionResponse(responseFuture, responseWriter, request, httpObjectCallback, synchronous);
            }

            @Override
            public void handleError(HttpResponse httpResponse) {
                webSocketClientRegistry.unregisterForwardCallbackHandler(webSocketCorrelationId);
                actionHandler.writeResponseActionResponse(httpResponse, responseWriter, request, httpObjectCallback, synchronous);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId));
    }

}
