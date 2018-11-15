package org.mockserver.mock.action;

import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.callback.WebSocketResponseCallback;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.UUID;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;

/**
 * @author jamesdbloom
 */
public class HttpResponseObjectCallbackActionHandler {
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpResponseObjectCallbackActionHandler(HttpStateHandler httpStateHandler) {
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
    }

    public void handle(final ActionHandler actionHandler, final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous) {
        String clientId = httpObjectCallback.getClientId();
        String webSocketCorrelationId = UUID.randomUUID().toString();
        webSocketClientRegistry.registerCallbackHandler(webSocketCorrelationId, new WebSocketResponseCallback() {
            @Override
            public void handle(HttpResponse response) {
                actionHandler.writeResponseActionResponse(response.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), responseWriter, request, httpObjectCallback, synchronous);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId));
    }

}
