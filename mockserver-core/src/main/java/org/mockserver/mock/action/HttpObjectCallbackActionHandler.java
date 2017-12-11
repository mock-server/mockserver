package org.mockserver.mock.action;

import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mockserver.callback.ExpectationCallbackResponse;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallbackActionHandler {
    private final LoggingFormatter logFormatter;
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpObjectCallbackActionHandler(HttpStateHandler httpStateHandler) {
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
        this.logFormatter = httpStateHandler.getLogFormatter();
    }

    public void handle(final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter) {
        String clientId = httpObjectCallback.getClientId();
        webSocketClientRegistry.registerCallbackResponseHandler(clientId, new ExpectationCallbackResponse() {
            @Override
            public void handle(HttpResponse response) {
                responseWriter.writeResponse(request, response);
                logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for object callback action:{}", response, request, httpObjectCallback);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request);
    }

}
