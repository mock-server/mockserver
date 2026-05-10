package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.closurecallback.websocketregistry.WebSocketRequestCallback;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.uuid.UUIDService;

import java.util.concurrent.CompletableFuture;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.model.HttpResponse.badGatewayResponse;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class HttpForwardObjectCallbackActionHandler extends HttpForwardAction {

    private WebSocketClientRegistry webSocketClientRegistry;

    public HttpForwardObjectCallbackActionHandler(HttpState httpStateHandler, Configuration configuration, NettyHttpClient httpClient) {
        super(httpStateHandler.getMockServerLogger(), configuration, httpClient);
        this.webSocketClientRegistry = httpStateHandler.getWebSocketClientRegistry();
    }

    public void handle(final HttpActionHandler actionHandler, final HttpObjectCallback httpObjectCallback, final HttpRequest request, final ResponseWriter responseWriter, final boolean synchronous, Runnable expectationPostProcessor) {
        final String clientId = httpObjectCallback.getClientId();
        if (LocalCallbackRegistry.forwardClientExists(clientId)) {
            handleLocally(actionHandler, httpObjectCallback, request, responseWriter, synchronous, expectationPostProcessor, clientId);
        } else {
            handleViaWebSocket(actionHandler, httpObjectCallback, request, responseWriter, synchronous, expectationPostProcessor, clientId);
        }
    }

    private void handleLocally(HttpActionHandler actionHandler, HttpObjectCallback httpObjectCallback, HttpRequest request, ResponseWriter responseWriter, boolean synchronous, Runnable expectationPostProcessor, String clientId) {
        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("locally sending request{}to client " + clientId)
                    .setArguments(request)
            );
        }
        ExpectationForwardCallback expectationForwardCallback = LocalCallbackRegistry.retrieveForwardCallback(clientId);
        try {
            HttpRequest callbackRequest = expectationForwardCallback.handle(request);
            final HttpForwardActionResult responseFuture = sendRequest(
                callbackRequest,
                null,
                null
            );
            ExpectationForwardAndResponseCallback expectationForwardAndResponseCallback = LocalCallbackRegistry.retrieveForwardAndResponseCallback(clientId);
            if (expectationForwardAndResponseCallback != null) {
                actionHandler.executeAfterForwardActionResponse(responseFuture, (httpResponse, exception) -> {
                    if (httpResponse != null) {
                        try {
                            HttpResponse callbackResponse = expectationForwardAndResponseCallback.handle(callbackRequest, httpResponse);
                            actionHandler.writeForwardActionResponse(callbackResponse, responseWriter, request, httpObjectCallback);
                            if (expectationPostProcessor != null) {
                                expectationPostProcessor.run();
                            }
                        } catch (Throwable throwable) {
                            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(WARN)
                                        .setHttpRequest(request)
                                        .setMessageFormat("returning{}because client " + clientId + " response callback threw an exception")
                                        .setArguments(badGatewayResponse())
                                        .setThrowable(throwable)
                                );
                            }
                            actionHandler.writeForwardActionResponse(badGatewayFuture(request), responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
                        }
                    } else if (exception != null) {
                        actionHandler.handleExceptionDuringForwardingRequest(httpObjectCallback, request, responseWriter, exception);
                        if (expectationPostProcessor != null) {
                            expectationPostProcessor.run();
                        }
                    }
                }, synchronous);
            } else {
                actionHandler.writeForwardActionResponse(responseFuture, responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
            }
        } catch (Throwable throwable) {
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setHttpRequest(request)
                        .setMessageFormat("returning{}because client " + clientId + " request callback throw an exception")
                        .setArguments(badGatewayResponse())
                        .setThrowable(throwable)
                );
            }
            actionHandler.writeForwardActionResponse(badGatewayFuture(request), responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
        }
    }

    private void handleViaWebSocket(HttpActionHandler actionHandler, HttpObjectCallback httpObjectCallback, HttpRequest request, ResponseWriter responseWriter, boolean synchronous, Runnable expectationPostProcessor, String clientId) {
        final String webSocketCorrelationId = UUIDService.getUUID();
        webSocketClientRegistry.registerForwardCallbackHandler(webSocketCorrelationId, new WebSocketRequestCallback() {
            @Override
            public void handle(final HttpRequest callbackRequest) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setHttpRequest(request)
                            .setMessageFormat("received request over websocket{}from client " + clientId + " for correlationId " + webSocketCorrelationId)
                            .setArguments(callbackRequest)
                    );
                }
                final HttpForwardActionResult responseFuture = sendRequest(
                    callbackRequest.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME),
                    null,
                    null
                );
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setHttpRequest(request)
                            .setMessageFormat("received response for request{}from client " + clientId)
                            .setArguments(callbackRequest)
                    );
                }
                webSocketClientRegistry.unregisterForwardCallbackHandler(webSocketCorrelationId);
                if (isTrue(httpObjectCallback.getResponseCallback())) {
                    handleResponseViaWebSocket(callbackRequest, responseFuture, actionHandler, webSocketCorrelationId, clientId, expectationPostProcessor, responseWriter, httpObjectCallback, synchronous);
                } else {
                    actionHandler.writeForwardActionResponse(responseFuture, responseWriter, callbackRequest, httpObjectCallback, synchronous, expectationPostProcessor);
                }
            }

            @Override
            public void handleError(HttpResponse httpResponse) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setHttpRequest(request)
                            .setMessageFormat("error sending request over websocket for client " + clientId + " for correlationId " + webSocketCorrelationId)
                    );
                }
                webSocketClientRegistry.unregisterForwardCallbackHandler(webSocketCorrelationId);
                actionHandler.writeResponseActionResponse(httpResponse, responseWriter, request, httpObjectCallback, synchronous, null, expectationPostProcessor);
            }
        });
        if (!webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId), null)) {
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setHttpRequest(request)
                        .setMessageFormat("returning{}because client " + clientId + " has closed web socket connection")
                        .setArguments(badGatewayResponse())
                );
            }
            actionHandler.writeForwardActionResponse(badGatewayFuture(request), responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
        } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("sending request over websocket{}to client " + clientId + " for correlationId " + webSocketCorrelationId)
                    .setArguments(request)
            );
        }
    }

    private void handleResponseViaWebSocket(HttpRequest request, HttpForwardActionResult responseFuture, HttpActionHandler actionHandler, String webSocketCorrelationId, String clientId, Runnable expectationPostProcessor, ResponseWriter responseWriter, HttpObjectCallback httpObjectCallback, boolean synchronous) {
        actionHandler.executeAfterForwardActionResponse(responseFuture, (httpResponse, exception) -> {
            if (httpResponse != null) {
                // register callback for overridden response
                CompletableFuture<HttpResponse> httpResponseCompletableFuture = new CompletableFuture<>();
                webSocketClientRegistry.registerResponseCallbackHandler(webSocketCorrelationId, overriddenResponse -> {
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setHttpRequest(request)
                                .setMessageFormat("received response over websocket{}for request and response{}from client " + clientId + " for correlationId " + webSocketCorrelationId)
                                .setArguments(
                                    overriddenResponse,
                                    new HttpRequestAndHttpResponse()
                                        .withHttpRequest(request)
                                        .withHttpResponse(httpResponse)
                                )
                        );
                    }
                    webSocketClientRegistry.unregisterResponseCallbackHandler(webSocketCorrelationId);
                    httpResponseCompletableFuture.complete(overriddenResponse.removeHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME));
                });
                // send websocket message to override response
                if (!webSocketClientRegistry.sendClientMessage(clientId, request.clone().withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId), httpResponse)) {
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setHttpRequest(request)
                                .setMessageFormat("returning{}because client " + clientId + " has closed web socket connection")
                                .setArguments(badGatewayResponse())
                        );
                    }
                    actionHandler.writeForwardActionResponse(badGatewayFuture(request), responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
                } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setHttpRequest(request)
                            .setMessageFormat("sending response over websocket{}to client " + clientId + " for correlationId " + webSocketCorrelationId)
                            .setArguments(httpResponse)
                    );
                }
                // return overridden response
                actionHandler.writeForwardActionResponse(responseFuture.setHttpResponse(httpResponseCompletableFuture), responseWriter, request, httpObjectCallback, synchronous, expectationPostProcessor);
            } else if (exception != null) {
                actionHandler.handleExceptionDuringForwardingRequest(httpObjectCallback, request, responseWriter, exception);
                if (expectationPostProcessor != null) {
                    expectationPostProcessor.run();
                }
            }
        }, synchronous);
    }

}
