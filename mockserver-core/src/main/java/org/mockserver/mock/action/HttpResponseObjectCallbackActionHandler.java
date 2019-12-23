package org.mockserver.mock.action;

import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.ResponseWriter;

import java.util.UUID;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class HttpResponseObjectCallbackActionHandler {
    private WebSocketClientRegistry webSocketClientRegistry;
    private final MockServerLogger mockServerLogger;

    public HttpResponseObjectCallbackActionHandler(HttpStateHandler httpStateHandler) {
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
    }

    public void handle(final ActionHandler actionHandler, final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous, Runnable expectationPostProcessor) {
        final String clientId = httpObjectCallback.getClientId();
        final String webSocketCorrelationId = UUID.randomUUID().toString();
        webSocketClientRegistry.registerResponseCallbackHandler(webSocketCorrelationId, response -> {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("Received response for request {} from client " + clientId)
                    .setArguments(request)
            );
            webSocketClientRegistry.unregisterResponseCallbackHandler(webSocketCorrelationId);
            if (expectationPostProcessor != null) {
                expectationPostProcessor.run();
            }
            actionHandler.writeResponseActionResponse(response.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), responseWriter, request, httpObjectCallback, synchronous);
        });
        if (!webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId), null)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.WARN)
                    .setLogLevel(WARN)
                    .setHttpRequest(request)
                    .setMessageFormat("Returning {} because client " + clientId + " has closed web socket connection")
                    .setArguments(notFoundResponse())
            );
            actionHandler.writeResponseActionResponse(notFoundResponse(), responseWriter, request, httpObjectCallback, synchronous);
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("Sending request {} to client " + clientId)
                    .setArguments(request)
            );
        }
    }

}
