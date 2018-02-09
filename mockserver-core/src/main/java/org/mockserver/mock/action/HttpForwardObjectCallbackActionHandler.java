package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.callback.WebSocketRequestCallback;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.UUID;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpForwardObjectCallbackActionHandler extends HttpForwardAction {
    private final MockServerLogger logFormatter;
    private final Scheduler scheduler;
    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpForwardObjectCallbackActionHandler(HttpStateHandler httpStateHandler, NettyHttpClient httpClient) {
        super(httpStateHandler.getMockServerLogger(), httpClient);
        this.scheduler = httpStateHandler.getScheduler();
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
        this.logFormatter = httpStateHandler.getMockServerLogger();
    }

    public void handle(final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous) {
        String clientId = httpObjectCallback.getClientId();
        String webSocketCorrelationId = UUID.randomUUID().toString();
        webSocketClientRegistry.registerCallbackHandler(webSocketCorrelationId, new WebSocketRequestCallback() {
            @Override
            public void handle(final HttpRequest request) {
                final SettableFuture<HttpResponse> responseFuture = sendRequest(request.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), null);
                scheduler.submit(responseFuture, new Runnable() {
                    public void run() {
                        try {
                            HttpResponse response = responseFuture.get();
                            responseWriter.writeResponse(request, response, false);
                            logFormatter.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for object callback action:{}", response, request, httpObjectCallback);
                        } catch (Exception ex) {
                            logFormatter.error(request, ex, ex.getMessage());
                        }
                    }
                }, synchronous);
            }
        });
        webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId));
    }

}
