package org.mockserver.mock.action;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.callback.WebSocketResponseCallback;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpResponseObjectCallbackActionHandler {
    private final MockServerLogger logFormatter;
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpResponseObjectCallbackActionHandler(HttpStateHandler httpStateHandler) {
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
        this.logFormatter = httpStateHandler.getMockServerLogger();
    }

    public void handle(final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter) {
        String clientId = httpObjectCallback.getClientId();
        webSocketClientRegistry.registerCallbackHandler(clientId, new WebSocketResponseCallback() {
            @Override
            public void handle(HttpResponse response) {
                responseWriter.writeResponse(request, response, false);
                logFormatter.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for object callback action:{}", response, request, httpObjectCallback);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request);
    }

}
