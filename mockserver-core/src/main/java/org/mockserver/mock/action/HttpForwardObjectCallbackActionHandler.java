package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.callback.WebSocketRequestCallback;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.scheduler.Scheduler.submit;

/**
 * @author jamesdbloom
 */
public class HttpForwardObjectCallbackActionHandler extends HttpForwardAction {
    private final MockServerLogger logFormatter;
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpForwardObjectCallbackActionHandler(HttpStateHandler httpStateHandler) {
        super(httpStateHandler.getMockServerLogger());
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
        this.logFormatter = httpStateHandler.getMockServerLogger();
    }

    public void handle(final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous) {
        String clientId = httpObjectCallback.getClientId();
        webSocketClientRegistry.registerCallbackHandler(clientId, new WebSocketRequestCallback() {
            @Override
            public void handle(final HttpRequest request) {
                final SettableFuture<HttpResponse> responseFuture = sendRequest(request, null);
                submit(responseFuture, new Runnable() {
                    public void run() {
                        try {
                            HttpResponse response = responseFuture.get();
                            responseWriter.writeResponse(request, response, false);
                            logFormatter.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for object callback action:{}", request, request, httpObjectCallback);
                        } catch (Exception ex) {
                            logFormatter.error(request, ex, ex.getMessage());
                        }
                    }
                }, synchronous);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request);
    }

}
