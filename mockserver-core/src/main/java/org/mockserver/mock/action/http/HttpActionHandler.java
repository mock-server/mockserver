package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.AttributeKey;
import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.configuration.Configuration;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketCommunicationException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.*;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.exception.ExceptionHandling.*;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntryMessages.*;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "FieldMayBeFinal"})
public class HttpActionHandler {

    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");

    private final Configuration configuration;
    private final HttpState httpStateHandler;
    private final Scheduler scheduler;
    private MockServerLogger mockServerLogger;
    private HttpResponseActionHandler httpResponseActionHandler;
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;
    private HttpResponseClassCallbackActionHandler httpResponseClassCallbackActionHandler;
    private HttpResponseObjectCallbackActionHandler httpResponseObjectCallbackActionHandler;
    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;
    private HttpForwardClassCallbackActionHandler httpForwardClassCallbackActionHandler;
    private HttpForwardObjectCallbackActionHandler httpForwardObjectCallbackActionHandler;
    private HttpOverrideForwardedRequestActionHandler httpOverrideForwardedRequestCallbackActionHandler;
    private HttpErrorActionHandler httpErrorActionHandler;

    // forwarding
    private NettyHttpClient httpClient;
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer;

    public HttpActionHandler(Configuration configuration, EventLoopGroup eventLoopGroup, HttpState httpStateHandler, List<ProxyConfiguration> proxyConfigurations, NettySslContextFactory nettySslContextFactory) {
        this.configuration = configuration;
        this.httpStateHandler = httpStateHandler;
        this.scheduler = httpStateHandler.getScheduler();
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);
        this.httpClient = new NettyHttpClient(configuration, mockServerLogger, eventLoopGroup, proxyConfigurations, true, nettySslContextFactory);
    }

    public void processAction(final HttpRequest request, final ResponseWriter responseWriter, final ChannelHandlerContext ctx, Set<String> localAddresses, boolean proxyingRequest, final boolean synchronous) {
        if (request.getHeaders() == null || !request.getHeaders().containsEntry(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(RECEIVED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request)
            );
        }
        final Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        Runnable expectationPostProcessor = () -> httpStateHandler.postProcess(expectation);
        final boolean potentiallyHttpProxy = !proxyingRequest && configuration.attemptToProxyIfNoMatchingExpectation() && !isEmpty(request.getFirstHeader(HOST.toString())) && !localAddresses.contains(request.getFirstHeader(HOST.toString()));

        if (expectation != null && expectation.getAction() != null) {

            final Action action = expectation.getAction();
            switch (action.getType()) {
                case RESPONSE: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpResponse response = getHttpResponseActionHandler().handle((HttpResponse) action);
                        writeResponseActionResponse(response, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous);
                    break;
                }
                case RESPONSE_TEMPLATE: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpResponse response = getHttpResponseTemplateActionHandler().handle((HttpTemplate) action, request);
                        writeResponseActionResponse(response, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case RESPONSE_CLASS_CALLBACK: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpResponse response = getHttpResponseClassCallbackActionHandler().handle((HttpClassCallback) action, request);
                        writeResponseActionResponse(response, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case RESPONSE_OBJECT_CALLBACK: {
                    scheduler.schedule(() ->
                            getHttpResponseObjectCallbackActionHandler().handle(HttpActionHandler.this, (HttpObjectCallback) action, request, responseWriter, synchronous, expectationPostProcessor),
                        synchronous, action.getDelay());
                    break;
                }
                case FORWARD: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpForwardActionResult responseFuture = getHttpForwardActionHandler().handle((HttpForward) action, request);
                        writeForwardActionResponse(responseFuture, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case FORWARD_TEMPLATE: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpForwardActionResult responseFuture = getHttpForwardTemplateActionHandler().handle((HttpTemplate) action, request);
                        writeForwardActionResponse(responseFuture, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case FORWARD_CLASS_CALLBACK: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpForwardActionResult responseFuture = getHttpForwardClassCallbackActionHandler().handle((HttpClassCallback) action, request);
                        writeForwardActionResponse(responseFuture, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case FORWARD_OBJECT_CALLBACK: {
                    scheduler.schedule(() ->
                            getHttpForwardObjectCallbackActionHandler().handle(HttpActionHandler.this, (HttpObjectCallback) action, request, responseWriter, synchronous, expectationPostProcessor),
                        synchronous, action.getDelay());
                    break;
                }
                case FORWARD_REPLACE: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        final HttpForwardActionResult responseFuture = getHttpOverrideForwardedRequestCallbackActionHandler().handle((HttpOverrideForwardedRequest) action, request);
                        writeForwardActionResponse(responseFuture, responseWriter, request, action, synchronous);
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
                case ERROR: {
                    scheduler.schedule(() -> handleAnyException(request, responseWriter, synchronous, action, () -> {
                        getHttpErrorActionHandler().handle((HttpError) action, ctx);
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(EXPECTATION_RESPONSE)
                                .setLogLevel(Level.INFO)
                                .setCorrelationId(request.getLogCorrelationId())
                                .setHttpRequest(request)
                                .setHttpError((HttpError) action)
                                .setExpectationId(action.getExpectationId())
                                .setMessageFormat("returning error:{}for request:{}for action:{}from expectation:{}")
                                .setArguments(action, request, action, action.getExpectationId())
                        );
                        expectationPostProcessor.run();
                    }), synchronous, action.getDelay());
                    break;
                }
            }

        } else if (CORSHeaders.isPreflightRequest(configuration, request) && (configuration.enableCORSForAPI() || configuration.enableCORSForAllResponses())) {

            responseWriter.writeResponse(request, OK);
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(INFO)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setMessageFormat("returning CORS response for OPTIONS request")
                );
            }

        } else if (proxyingRequest || potentiallyHttpProxy) {

            if (request.getHeaders() != null && request.getHeaders().containsEntry(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue())) {

                if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setMessageFormat("received \"x-forwarded-by\" header caused by exploratory HTTP proxy or proxy loop - falling back to no proxy:{}")
                            .setArguments(request)
                    );
                }
                returnNotFound(responseWriter, request, null);

            } else {

                String username = configuration.proxyAuthenticationUsername();
                String password = configuration.proxyAuthenticationPassword();
                // only authenticate potentiallyHttpProxy because other proxied requests should have already been authenticated (i.e. in CONNECT request)
                if (potentiallyHttpProxy && isNotBlank(username) && isNotBlank(password) &&
                    !request.containsHeader(PROXY_AUTHORIZATION.toString(), "Basic " + Base64.encode(Unpooled.copiedBuffer(username + ':' + password, StandardCharsets.UTF_8), false).toString(StandardCharsets.US_ASCII))) {

                    HttpResponse response = response()
                        .withStatusCode(PROXY_AUTHENTICATION_REQUIRED.code())
                        .withHeader(PROXY_AUTHENTICATE.toString(), "Basic realm=\"" + StringEscapeUtils.escapeJava(configuration.proxyAuthenticationRealm()) + "\", charset=\"UTF-8\"");
                    responseWriter.writeResponse(request, response, false);
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(AUTHENTICATION_FAILED)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setHttpRequest(request)
                            .setHttpResponse(response)
                            .setExpectation(request, response)
                            .setMessageFormat("proxy authentication failed so returning response:{}for forwarded request:{}")
                            .setArguments(response, request)
                    );

                } else {

                    final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
                    final HttpRequest clonedRequest = hopByHopHeaderFilter.onRequest(request).withHeader(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue());
                    final HttpForwardActionResult responseFuture = new HttpForwardActionResult(clonedRequest, httpClient.sendRequest(clonedRequest, remoteAddress, potentiallyHttpProxy ? 1000 : configuration.socketConnectionTimeoutInMillis()), null, remoteAddress);
                    scheduler.submit(responseFuture, () -> {
                            try {
                                HttpResponse response = responseFuture.getHttpResponse().get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
                                if (response == null) {
                                    response = notFoundResponse();
                                }
                                if (response.containsHeader(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
                                    response.removeHeader(httpStateHandler.getUniqueLoopPreventionHeaderName());
                                    if (MockServerLogger.isEnabled(Level.INFO)) {
                                        mockServerLogger.logEvent(
                                            new LogEntry()
                                                .setType(NO_MATCH_RESPONSE)
                                                .setLogLevel(Level.INFO)
                                                .setCorrelationId(request.getLogCorrelationId())
                                                .setHttpRequest(request)
                                                .setHttpResponse(notFoundResponse())
                                                .setMessageFormat(NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT)
                                                .setArguments(request, response)
                                        );
                                    }
                                } else {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setType(FORWARDED_REQUEST)
                                            .setLogLevel(Level.INFO)
                                            .setCorrelationId(request.getLogCorrelationId())
                                            .setHttpRequest(request)
                                            .setHttpResponse(response)
                                            .setExpectation(request, response)
                                            .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}")
                                            .setArguments(response, request, httpRequestToCurlSerializer.toCurl(request, remoteAddress))
                                    );
                                }
                                responseWriter.writeResponse(request, response, false);
                            } catch (SocketCommunicationException sce) {
                                returnNotFound(responseWriter, request, sce.getMessage());
                            } catch (Throwable throwable) {
                                if (potentiallyHttpProxy && connectionException(throwable)) {
                                    if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                                        mockServerLogger.logEvent(
                                            new LogEntry()
                                                .setLogLevel(TRACE)
                                                .setCorrelationId(request.getLogCorrelationId())
                                                .setMessageFormat("failed to connect to proxied socket due to exploratory HTTP proxy for:{}due to:{}falling back to no proxy")
                                                .setArguments(request, throwable.getCause())
                                        );
                                    }
                                    returnNotFound(responseWriter, request, null);
                                } else if (sslHandshakeException(throwable)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(Level.ERROR)
                                            .setCorrelationId(request.getLogCorrelationId())
                                            .setHttpRequest(request)
                                            .setMessageFormat("TLS handshake exception while proxying request{}to remote address{}with channel" + (ctx != null ? String.valueOf(ctx.channel()) : ""))
                                            .setArguments(request, remoteAddress)
                                            .setThrowable(throwable)
                                    );
                                    returnNotFound(responseWriter, request, "TLS handshake exception while proxying request to remote address" + remoteAddress);
                                } else if (!connectionClosedException(throwable)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setType(EXCEPTION)
                                            .setLogLevel(Level.ERROR)
                                            .setCorrelationId(request.getLogCorrelationId())
                                            .setHttpRequest(request)
                                            .setMessageFormat(throwable.getMessage())
                                            .setThrowable(throwable)
                                    );
                                    returnNotFound(responseWriter, request, "connection closed while proxying request to remote address" + remoteAddress);
                                } else {
                                    returnNotFound(responseWriter, request, throwable.getMessage());
                                }
                            }
                        },
                        synchronous,
                        throwable -> !(potentiallyHttpProxy && isNotBlank(throwable.getMessage()) || !throwable.getMessage().contains("Connection refused"))
                    );

                }

            }

        } else {

            returnNotFound(responseWriter, request, null);

        }
    }

    private void handleAnyException(HttpRequest request, ResponseWriter responseWriter, boolean synchronous, Action action, Runnable processAction) {
        try {
            processAction.run();
        } catch (Throwable throwable) {
            writeResponseActionResponse(notFoundResponse(), responseWriter, request, action, synchronous);
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(WARN)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setHttpRequest(request)
                        .setMessageFormat(throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    void writeResponseActionResponse(final HttpResponse response, final ResponseWriter responseWriter, final HttpRequest request, final Action action, boolean synchronous) {
        scheduler.schedule(() -> {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(EXPECTATION_RESPONSE)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setHttpResponse(response)
                    .setExpectationId(action.getExpectationId())
                    .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}")
                    .setArguments(response, request, action, action.getExpectationId())
            );
            responseWriter.writeResponse(request, response, false);
        }, synchronous, response.getDelay());
    }

    void executeAfterForwardActionResponse(final HttpForwardActionResult responseFuture, final BiConsumer<HttpResponse, Throwable> command, final boolean synchronous) {
        scheduler.submit(responseFuture, command, synchronous);
    }

    void writeForwardActionResponse(final HttpForwardActionResult responseFuture, final ResponseWriter responseWriter, final HttpRequest request, final Action action, boolean synchronous) {
        scheduler.submit(responseFuture, () -> {
            try {
                HttpResponse response = responseFuture.getHttpResponse().get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
                responseWriter.writeResponse(request, response, false);
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(FORWARDED_REQUEST)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setHttpRequest(request)
                        .setHttpResponse(response)
                        .setExpectation(request, response)
                        .setExpectationId(action.getExpectationId())
                        .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                        .setArguments(response, responseFuture.getHttpRequest(), httpRequestToCurlSerializer.toCurl(responseFuture.getHttpRequest(), responseFuture.getRemoteAddress()), action, action.getExpectationId())
                );
            } catch (Throwable throwable) {
                handleExceptionDuringForwardingRequest(action, request, responseWriter, throwable);
            }
        }, synchronous, throwable -> true);
    }

    void writeForwardActionResponse(final HttpResponse response, final ResponseWriter responseWriter, final HttpRequest request, final Action action) {
        try {
            responseWriter.writeResponse(request, response, false);
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setHttpResponse(response)
                    .setExpectation(request, response)
                    .setExpectationId(action.getExpectationId())
                    .setMessageFormat("returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}" + NEW_LINE + NEW_LINE + " in curl:{}for action:{}from expectation:{}")
                    .setArguments(response, response, httpRequestToCurlSerializer.toCurl(request), action, action.getExpectationId())
            );
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setMessageFormat(throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    void handleExceptionDuringForwardingRequest(Action action, HttpRequest request, ResponseWriter responseWriter, Throwable exception) {
        if (connectionException(exception)) {
            if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setMessageFormat("failed to connect to remote socket while forwarding request{}for action{}")
                        .setArguments(request, action)
                        .setThrowable(exception)
                );
            }
            returnNotFound(responseWriter, request, "failed to connect to remote socket while forwarding request");
        } else if (sslHandshakeException(exception)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setMessageFormat("TLS handshake exception while forwarding request{}for action{}")
                    .setArguments(request, action)
                    .setThrowable(exception)
            );
            returnNotFound(responseWriter, request, "TLS handshake exception while forwarding request");
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setCorrelationId(request.getLogCorrelationId())
                    .setHttpRequest(request)
                    .setMessageFormat(exception != null ? isNotBlank(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName() : null)
                    .setThrowable(exception)
            );
            returnNotFound(responseWriter, request, exception != null ? exception.getMessage() : null);
        }
    }

    private void returnNotFound(ResponseWriter responseWriter, HttpRequest request, String error) {
        HttpResponse response = notFoundResponse();
        if (request.getHeaders() != null && request.getHeaders().containsEntry(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
            response.withHeader(httpStateHandler.getUniqueLoopPreventionHeaderName(), httpStateHandler.getUniqueLoopPreventionHeaderValue());
            if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setHttpRequest(request)
                        .setMessageFormat(NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT)
                        .setArguments(request, notFoundResponse())
                );
            }
        } else if (isNotBlank(error)) {
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(NO_MATCH_RESPONSE)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setHttpRequest(request)
                        .setHttpResponse(notFoundResponse())
                        .setMessageFormat(NO_MATCH_RESPONSE_ERROR_MESSAGE_FORMAT)
                        .setArguments(error, request, notFoundResponse())
                );
            }
        } else {
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(NO_MATCH_RESPONSE)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(request.getLogCorrelationId())
                        .setHttpRequest(request)
                        .setHttpResponse(notFoundResponse())
                        .setMessageFormat(NO_MATCH_RESPONSE_NO_EXPECTATION_MESSAGE_FORMAT)
                        .setArguments(request, notFoundResponse())
                );
            }
        }
        responseWriter.writeResponse(request, response, false);
    }

    private HttpResponseActionHandler getHttpResponseActionHandler() {
        if (httpResponseActionHandler == null) {
            httpResponseActionHandler = new HttpResponseActionHandler();
        }
        return httpResponseActionHandler;
    }

    private HttpResponseTemplateActionHandler getHttpResponseTemplateActionHandler() {
        if (httpResponseTemplateActionHandler == null) {
            httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(mockServerLogger, configuration);
        }
        return httpResponseTemplateActionHandler;
    }

    private HttpResponseClassCallbackActionHandler getHttpResponseClassCallbackActionHandler() {
        if (httpResponseClassCallbackActionHandler == null) {
            httpResponseClassCallbackActionHandler = new HttpResponseClassCallbackActionHandler(mockServerLogger);
        }
        return httpResponseClassCallbackActionHandler;
    }

    private HttpResponseObjectCallbackActionHandler getHttpResponseObjectCallbackActionHandler() {
        if (httpResponseObjectCallbackActionHandler == null) {
            httpResponseObjectCallbackActionHandler = new HttpResponseObjectCallbackActionHandler(httpStateHandler);
        }
        return httpResponseObjectCallbackActionHandler;
    }

    private HttpForwardActionHandler getHttpForwardActionHandler() {
        if (httpForwardActionHandler == null) {
            httpForwardActionHandler = new HttpForwardActionHandler(mockServerLogger, httpClient);
        }
        return httpForwardActionHandler;
    }

    private HttpForwardTemplateActionHandler getHttpForwardTemplateActionHandler() {
        if (httpForwardTemplateActionHandler == null) {
            httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockServerLogger, configuration, httpClient);
        }
        return httpForwardTemplateActionHandler;
    }

    private HttpForwardClassCallbackActionHandler getHttpForwardClassCallbackActionHandler() {
        if (httpForwardClassCallbackActionHandler == null) {
            httpForwardClassCallbackActionHandler = new HttpForwardClassCallbackActionHandler(mockServerLogger, httpClient);
        }
        return httpForwardClassCallbackActionHandler;
    }

    private HttpForwardObjectCallbackActionHandler getHttpForwardObjectCallbackActionHandler() {
        if (httpForwardObjectCallbackActionHandler == null) {
            httpForwardObjectCallbackActionHandler = new HttpForwardObjectCallbackActionHandler(httpStateHandler, httpClient);
        }
        return httpForwardObjectCallbackActionHandler;
    }

    private HttpOverrideForwardedRequestActionHandler getHttpOverrideForwardedRequestCallbackActionHandler() {
        if (httpOverrideForwardedRequestCallbackActionHandler == null) {
            httpOverrideForwardedRequestCallbackActionHandler = new HttpOverrideForwardedRequestActionHandler(mockServerLogger, httpClient);
        }
        return httpOverrideForwardedRequestCallbackActionHandler;
    }

    private HttpErrorActionHandler getHttpErrorActionHandler() {
        if (httpErrorActionHandler == null) {
            httpErrorActionHandler = new HttpErrorActionHandler();
        }
        return httpErrorActionHandler;
    }

    public NettyHttpClient getHttpClient() {
        return httpClient;
    }


    public static InetSocketAddress getRemoteAddress(final ChannelHandlerContext ctx) {
        if (ctx != null && ctx.channel() != null && ctx.channel().attr(REMOTE_SOCKET) != null) {
            return ctx.channel().attr(REMOTE_SOCKET).get();
        } else {
            return null;
        }
    }
}
