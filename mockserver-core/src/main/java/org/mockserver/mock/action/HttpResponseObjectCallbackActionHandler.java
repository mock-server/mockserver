package org.mockserver.mock.action;

import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import java.util.UUID;

import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
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
        if (LocalCallbackRegistry.responseClientExists(clientId)) {
            handleLocally(actionHandler, httpObjectCallback, request, responseWriter, synchronous, clientId);
        } else {
            handleViaWebSocket(actionHandler, httpObjectCallback, request, responseWriter, synchronous, expectationPostProcessor, clientId);
        }
    }

    private void handleLocally(ActionHandler actionHandler, HttpObjectCallback httpObjectCallback, HttpRequest request, ResponseWriter responseWriter, boolean synchronous, String clientId) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(TRACE)
                .setHttpRequest(request)
                .setMessageFormat("locally sending request{}to client " + clientId)
                .setArguments(request)
        );
        try {
            HttpResponse callbackResponse = LocalCallbackRegistry.retrieveResponseCallback(clientId).handle(request);
            actionHandler.writeResponseActionResponse(callbackResponse, responseWriter, request, httpObjectCallback, synchronous);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(WARN)
                    .setHttpRequest(request)
                    .setMessageFormat("returning{}because client " + clientId + " response callback throw an exception")
                    .setArguments(notFoundResponse())
                    .setThrowable(throwable)
            );
            actionHandler.writeResponseActionResponse(notFoundResponse(), responseWriter, request, httpObjectCallback, synchronous);
        }
    }

    private void handleViaWebSocket(ActionHandler actionHandler, HttpObjectCallback httpObjectCallback, HttpRequest request, ResponseWriter responseWriter, boolean synchronous, Runnable expectationPostProcessor, String clientId) {
        final String webSocketCorrelationId = UUID.randomUUID().toString();
        webSocketClientRegistry.registerResponseCallbackHandler(webSocketCorrelationId, response -> {
            if (MockServerLogger.isEnabled(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setHttpRequest(request)
                        .setMessageFormat("received response over websocket{}for request{}from client " + clientId + " for correlationId " + webSocketCorrelationId)
                        .setArguments(response, request)
                );
            }
            webSocketClientRegistry.unregisterResponseCallbackHandler(webSocketCorrelationId);
            if (expectationPostProcessor != null) {
                expectationPostProcessor.run();
            }
            actionHandler.writeResponseActionResponse(response.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), responseWriter, request, httpObjectCallback, synchronous);
        });
        if (!webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId), null)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(WARN)
                    .setHttpRequest(request)
                    .setMessageFormat("returning{}because client " + clientId + " has closed web socket connection")
                    .setArguments(notFoundResponse())
            );
            actionHandler.writeResponseActionResponse(notFoundResponse(), responseWriter, request, httpObjectCallback, synchronous);
        } else if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("sending request over websocket{}to client " + clientId + " for correlationId " + webSocketCorrelationId)
                    .setArguments(request)
            );
        }
    }

}
