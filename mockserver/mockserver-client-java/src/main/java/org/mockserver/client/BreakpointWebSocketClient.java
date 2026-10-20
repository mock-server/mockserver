package org.mockserver.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpMessage;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.BreakpointReleasedDTO;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;
import org.mockserver.serialization.model.WebSocketClientIdDTO;
import org.mockserver.serialization.model.WebSocketErrorDTO;
import org.mockserver.socket.NettyAllocator;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.BREAKPOINT_ID_HEADER_NAME;
import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

/**
 * WebSocket client for breakpoint callback resolution. Connects to the server's
 * {@code /_mockserver_callback_websocket} endpoint with a given {@code clientId}
 * and handles breakpoint messages:
 *
 * <ul>
 *   <li>{@link HttpRequest} -- REQUEST phase breakpoint; dispatched to
 *       {@link BreakpointRequestHandler}</li>
 *   <li>{@link HttpRequestAndHttpResponse} -- RESPONSE phase breakpoint; dispatched to
 *       {@link BreakpointResponseHandler}</li>
 *   <li>{@link PausedStreamFrameDTO} -- RESPONSE_STREAM / INBOUND_STREAM phase breakpoint;
 *       dispatched to {@link BreakpointStreamFrameHandler}</li>
 * </ul>
 *
 * <p>Handlers are stored per breakpoint id (the id returned by the server when a
 * breakpoint matcher is registered). Each pushed paused item carries the matched
 * breakpoint id (as a {@code X-MockServer-BreakpointId} header for REQUEST/RESPONSE,
 * or a {@code breakpointId} field for stream frames), and is routed to the handler
 * registered for that specific breakpoint. If the id is absent or unknown, the item
 * is auto-continued with DEBUG logging.
 *
 * <p>This is separate from the existing {@code WebSocketClient} (which serves
 * expectation object callbacks) so it can be created in the client module
 * without modifying server-core code.
 */
class BreakpointWebSocketClient {

    static final String CLIENT_REGISTRATION_ID_HEADER = "X-CLIENT-REGISTRATION-ID";
    static final String CAPABILITIES_HEADER = "X-MockServer-Capabilities";
    static final AttributeKey<CompletableFuture<String>> REGISTRATION_FUTURE = AttributeKey.valueOf("BP_REGISTRATION_FUTURE");

    private final MockServerLogger mockServerLogger;
    private final WebSocketMessageSerializer webSocketMessageSerializer;
    private final EventLoopGroup eventLoopGroup;
    private final String clientId;
    private final Supplier<SslContext> sslContextSupplier;
    private volatile Supplier<String> authorizationSupplier;
    private volatile Channel channel;
    private volatile boolean isStopped = false;

    // Per-breakpoint-id handlers
    private final ConcurrentHashMap<String, BreakpointRequestHandler> requestHandlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BreakpointResponseHandler> responseHandlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BreakpointStreamFrameHandler> streamFrameHandlers = new ConcurrentHashMap<>();

    /**
     * @param sslContextSupplier the TLS context for a secure connection: the one {@code MockServerClient} uses for
     *                           its HTTP requests, so both trust the same server certificates
     */
    BreakpointWebSocketClient(EventLoopGroup eventLoopGroup, String clientId, MockServerLogger mockServerLogger, Supplier<SslContext> sslContextSupplier) {
        this.eventLoopGroup = eventLoopGroup;
        this.clientId = clientId;
        this.mockServerLogger = mockServerLogger;
        this.webSocketMessageSerializer = new WebSocketMessageSerializer(mockServerLogger);
        this.sslContextSupplier = sslContextSupplier;
    }

    String getClientId() {
        return clientId;
    }

    /**
     * @param authorizationSupplier the {@code Authorization} header value sent on each WebSocket upgrade, so the
     *                              upgrade passes control-plane authentication; a null or blank value sends none
     */
    BreakpointWebSocketClient withAuthorization(Supplier<String> authorizationSupplier) {
        this.authorizationSupplier = authorizationSupplier;
        return this;
    }

    /**
     * Register a request handler for the given breakpoint id.
     */
    void setRequestHandler(String breakpointId, BreakpointRequestHandler handler) {
        if (breakpointId != null && handler != null) {
            requestHandlers.put(breakpointId, handler);
        }
    }

    /**
     * Register a response handler for the given breakpoint id.
     */
    void setResponseHandler(String breakpointId, BreakpointResponseHandler handler) {
        if (breakpointId != null && handler != null) {
            responseHandlers.put(breakpointId, handler);
        }
    }

    /**
     * Register a stream frame handler for the given breakpoint id.
     */
    void setStreamFrameHandler(String breakpointId, BreakpointStreamFrameHandler handler) {
        if (breakpointId != null && handler != null) {
            streamFrameHandlers.put(breakpointId, handler);
        }
    }

    /**
     * Remove all handlers for the given breakpoint id.
     */
    void removeHandlers(String breakpointId) {
        if (breakpointId != null) {
            requestHandlers.remove(breakpointId);
            responseHandlers.remove(breakpointId);
            streamFrameHandlers.remove(breakpointId);
        }
    }

    /**
     * Remove all handlers for all breakpoints.
     */
    void clearHandlers() {
        requestHandlers.clear();
        responseHandlers.clear();
        streamFrameHandlers.clear();
    }

    Future<String> register(InetSocketAddress serverAddress, String contextPath, boolean isSecure) {
        return register(serverAddress, contextPath, isSecure, 3);
    }

    private Future<String> register(InetSocketAddress serverAddress, String contextPath, boolean isSecure, int reconnectAttempts) {
        CompletableFuture<String> registrationFuture = new CompletableFuture<>();
        try {
            final SslContext sslContext = isSecure ? sslContextSupplier.get() : null;
            final Supplier<String> supplier = authorizationSupplier;
            final String authorization = supplier != null ? supplier.get() : null;
            new Bootstrap()
                .group(this.eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, NettyAllocator.ALLOCATOR)
                .attr(REGISTRATION_FUTURE, registrationFuture)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws URISyntaxException {
                        if (sslContext != null) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), serverAddress.getHostName(), serverAddress.getPort()));
                        }

                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                        ch.pipeline().addLast(new BreakpointWebSocketClientHandler(
                            mockServerLogger, clientId, serverAddress, contextPath,
                            BreakpointWebSocketClient.this, isSecure, authorization
                        ));
                    }
                })
                .connect(serverAddress)
                .addListener((ChannelFutureListener) connectChannelFuture -> {
                    channel = connectChannelFuture.channel();
                    channel.closeFuture().addListener((ChannelFutureListener) closeChannelFuture -> {
                        if (!isStopped && reconnectAttempts > 0) {
                            register(serverAddress, contextPath, isSecure, reconnectAttempts - 1);
                        }
                    });
                });
        } catch (Exception e) {
            registrationFuture.completeExceptionally(new RuntimeException("Exception while starting breakpoint web socket client", e));
        }
        return registrationFuture;
    }

    void receivedTextWebSocketFrame(TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage;
            try {
                deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            } catch (IllegalArgumentException e) {
                if (e.getMessage() == null || !e.getMessage().startsWith("Unsupported WebSocket message type")) {
                    throw e;
                }
                // a newer server may send message types this client does not know; none needs a reply
                logIgnoredMessage(textWebSocketFrame.text());
                return;
            }
            if (deserializedMessage instanceof HttpRequest) {
                handleRequestPhase((HttpRequest) deserializedMessage);
            } else if (deserializedMessage instanceof HttpRequestAndHttpResponse) {
                handleResponsePhase((HttpRequestAndHttpResponse) deserializedMessage);
            } else if (deserializedMessage instanceof PausedStreamFrameDTO) {
                handleStreamFrame((PausedStreamFrameDTO) deserializedMessage);
            } else if (deserializedMessage instanceof WebSocketClientIdDTO || deserializedMessage instanceof BreakpointReleasedDTO) {
                // a released notice needs no reply: the server already resolved the paused item itself
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("breakpoint websocket client received{}")
                            .setArguments(deserializedMessage)
                    );
                }
            } else {
                logIgnoredMessage(textWebSocketFrame.text());
            }
        } catch (Exception e) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while processing breakpoint websocket message - " + e.getMessage())
                        .setThrowable(e)
                );
            }
        }
    }

    private void logIgnoredMessage(String message) {
        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat("breakpoint websocket client ignored a message type it does not handle{}")
                    .setArguments(message)
            );
        }
    }

    private void handleRequestPhase(HttpRequest request) {
        String webSocketCorrelationId = request.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
        String breakpointId = request.getFirstHeader(BREAKPOINT_ID_HEADER_NAME);

        BreakpointRequestHandler handler = (breakpointId != null) ? requestHandlers.get(breakpointId) : null;
        if (handler != null) {
            try {
                HttpMessage result = handler.handle(request);
                if (result == null) {
                    // Null return -- auto-continue with original request
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger.logEvent(new LogEntry().setLogLevel(WARN)
                            .setMessageFormat("breakpoint request handler returned null for breakpoint={}, auto-continuing with original request")
                            .setArguments(breakpointId));
                    }
                    result = request;
                }
                if (result instanceof HttpRequest) {
                    ((HttpRequest) result).withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                } else if (result instanceof HttpResponse) {
                    ((HttpResponse) result).withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                }
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(result)));
            } catch (Throwable throwable) {
                sendError(webSocketCorrelationId, throwable);
            }
        } else {
            // No handler for this breakpoint id -- auto-continue with original request
            if (breakpointId != null && mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                mockServerLogger.logEvent(new LogEntry().setLogLevel(DEBUG)
                    .setMessageFormat("no request handler for breakpoint={}, auto-continuing")
                    .setArguments(breakpointId));
            }
            request.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
            try {
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(request)));
            } catch (Exception e) {
                sendError(webSocketCorrelationId, e);
            }
        }
    }

    private void handleResponsePhase(HttpRequestAndHttpResponse requestAndResponse) {
        HttpRequest httpRequest = requestAndResponse.getHttpRequest();
        HttpResponse httpResponse = requestAndResponse.getHttpResponse();
        String webSocketCorrelationId = httpRequest.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
        String breakpointId = httpRequest.getFirstHeader(BREAKPOINT_ID_HEADER_NAME);

        BreakpointResponseHandler handler = (breakpointId != null) ? responseHandlers.get(breakpointId) : null;
        if (handler != null) {
            try {
                HttpResponse result = handler.handle(httpRequest, httpResponse);
                if (result == null) {
                    // Null return -- auto-continue with original response
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger.logEvent(new LogEntry().setLogLevel(WARN)
                            .setMessageFormat("breakpoint response handler returned null for breakpoint={}, auto-continuing with original response")
                            .setArguments(breakpointId));
                    }
                    result = httpResponse;
                }
                result.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(result)));
            } catch (Throwable throwable) {
                sendError(webSocketCorrelationId, throwable);
            }
        } else {
            // No handler for this breakpoint id -- auto-continue with original response
            if (breakpointId != null && mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                mockServerLogger.logEvent(new LogEntry().setLogLevel(DEBUG)
                    .setMessageFormat("no response handler for breakpoint={}, auto-continuing")
                    .setArguments(breakpointId));
            }
            httpResponse.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
            try {
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(httpResponse)));
            } catch (Exception e) {
                sendError(webSocketCorrelationId, e);
            }
        }
    }

    private void handleStreamFrame(PausedStreamFrameDTO pausedFrame) {
        String breakpointId = pausedFrame.getBreakpointId();

        BreakpointStreamFrameHandler handler = (breakpointId != null) ? streamFrameHandlers.get(breakpointId) : null;
        if (handler != null) {
            try {
                StreamFrameDecisionDTO decision = handler.handle(pausedFrame);
                if (decision == null) {
                    // Null return -- auto-continue
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger.logEvent(new LogEntry().setLogLevel(WARN)
                            .setMessageFormat("breakpoint stream frame handler returned null for breakpoint={}, auto-continuing")
                            .setArguments(breakpointId));
                    }
                    decision = new StreamFrameDecisionDTO()
                        .setCorrelationId(pausedFrame.getCorrelationId())
                        .setAction("CONTINUE");
                } else {
                    // ensure correlationId is echoed
                    decision.setCorrelationId(pausedFrame.getCorrelationId());
                }
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(decision)));
            } catch (Throwable throwable) {
                // on error, auto-continue
                try {
                    StreamFrameDecisionDTO continueDecision = new StreamFrameDecisionDTO()
                        .setCorrelationId(pausedFrame.getCorrelationId())
                        .setAction("CONTINUE");
                    channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(continueDecision)));
                } catch (Exception e) {
                    if (mockServerLogger != null) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("exception while sending auto-continue for stream frame - " + e.getMessage())
                                .setThrowable(e)
                        );
                    }
                }
            }
        } else {
            // No handler for this breakpoint id -- auto-continue
            if (breakpointId != null && mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                mockServerLogger.logEvent(new LogEntry().setLogLevel(DEBUG)
                    .setMessageFormat("no stream frame handler for breakpoint={}, auto-continuing")
                    .setArguments(breakpointId));
            }
            try {
                StreamFrameDecisionDTO continueDecision = new StreamFrameDecisionDTO()
                    .setCorrelationId(pausedFrame.getCorrelationId())
                    .setAction("CONTINUE");
                channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(continueDecision)));
            } catch (Exception e) {
                if (mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while sending auto-continue for stream frame - " + e.getMessage())
                            .setThrowable(e)
                    );
                }
            }
        }
    }

    private void sendError(String webSocketCorrelationId, Throwable throwable) {
        if (mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception thrown while handling breakpoint callback - " + throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
        try {
            channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(
                new WebSocketErrorDTO()
                    .setMessage(throwable.getMessage())
                    .setWebSocketCorrelationId(webSocketCorrelationId)
            )));
        } catch (Exception e) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while sending error response - " + e.getMessage())
                        .setThrowable(e)
                );
            }
        }
    }

    void stopClient() {
        isStopped = true;
        try {
            // MINOR E fix: close the channel BEFORE shutting down the event loop group
            // to avoid RejectedExecutionException from channel.close().sync() after group shutdown
            if (channel != null && channel.isOpen()) {
                channel.close().sync();
                channel = null;
            }
            if (eventLoopGroup != null && !eventLoopGroup.isShuttingDown()) {
                eventLoopGroup.shutdownGracefully();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Exception while closing breakpoint web socket client", e);
        }
    }

    /**
     * Netty handler for the breakpoint WS handshake and message dispatch.
     */
    static class BreakpointWebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

        private final BreakpointWebSocketClient webSocketClient;
        private final WebSocketClientHandshaker handshaker;
        private final MockServerLogger mockServerLogger;
        private final String clientId;

        BreakpointWebSocketClientHandler(MockServerLogger mockServerLogger, String clientId,
                                         InetSocketAddress serverAddress, String contextPath,
                                         BreakpointWebSocketClient webSocketClient, boolean isSecure)
            throws URISyntaxException {
            this(mockServerLogger, clientId, serverAddress, contextPath, webSocketClient, isSecure, null);
        }

        BreakpointWebSocketClientHandler(MockServerLogger mockServerLogger, String clientId,
                                         InetSocketAddress serverAddress, String contextPath,
                                         BreakpointWebSocketClient webSocketClient, boolean isSecure,
                                         String authorization)
            throws URISyntaxException {
            this.mockServerLogger = mockServerLogger;
            this.clientId = clientId;
            String cleanedContextPath = "";
            if (contextPath != null && !contextPath.isEmpty()) {
                cleanedContextPath = (!contextPath.startsWith("/") ? "/" : "") + contextPath;
            }
            this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                new URI((isSecure ? "wss" : "ws") + "://" + serverAddress.getHostName() + ":" + serverAddress.getPort()
                    + cleanedContextPath + "/_mockserver_callback_websocket"),
                WebSocketVersion.V13,
                null,
                false,
                // the capability is a header, not a query parameter: servers up to 8.0.0 open the
                // callback WebSocket only for the exact bare URI, and ignore headers they do not know
                upgradeHeaders(clientId, authorization),
                Integer.MAX_VALUE,
                true,
                false,
                10_000L,
                false,
                // no Origin: a non-browser client is not subject to MockServer's browser-origin check
                false
            );
            this.webSocketClient = webSocketClient;
        }

        private static DefaultHttpHeaders upgradeHeaders(String clientId, String authorization) {
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add(CLIENT_REGISTRATION_ID_HEADER, clientId).add(CAPABILITIES_HEADER, "breakpointReleased");
            if (authorization != null && !authorization.trim().isEmpty()) {
                headers.add("Authorization", authorization);
            }
            return headers;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("breakpoint web socket client disconnected")
                );
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            if (msg instanceof FullHttpResponse) {
                FullHttpResponse httpResponse = (FullHttpResponse) msg;
                final CompletableFuture<String> registrationFuture = ch.attr(REGISTRATION_FUTURE).get();
                if (httpResponse.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)
                    && !handshaker.isHandshakeComplete()) {
                    handshaker.finishHandshake(ch, httpResponse);
                    registrationFuture.complete(clientId);
                } else if (httpResponse.status().equals(HttpResponseStatus.NOT_IMPLEMENTED)) {
                    String message = readResponseBody(httpResponse);
                    registrationFuture.completeExceptionally(new RuntimeException(message));
                } else if (httpResponse.status().equals(HttpResponseStatus.RESET_CONTENT)) {
                    registrationFuture.complete(clientId);
                } else {
                    registrationFuture.completeExceptionally(new RuntimeException(
                        "handshake failure: unsupported response " + httpResponse.status()));
                }
            } else if (msg instanceof WebSocketFrame) {
                if (msg instanceof TextWebSocketFrame) {
                    webSocketClient.receivedTextWebSocketFrame((TextWebSocketFrame) msg);
                } else if (msg instanceof PingWebSocketFrame) {
                    ctx.write(new PongWebSocketFrame(((WebSocketFrame) msg).content().retain()));
                } else if (msg instanceof CloseWebSocketFrame) {
                    ch.close();
                }
            }
        }

        private String readResponseBody(FullHttpResponse httpResponse) {
            if (httpResponse.content().readableBytes() > 0) {
                byte[] bodyBytes = new byte[httpResponse.content().readableBytes()];
                httpResponse.content().readBytes(bodyBytes);
                return new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            return "";
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("breakpoint web socket client caught exception")
                        .setThrowable(cause)
                );
            }
            final CompletableFuture<String> registrationFuture = ctx.channel().attr(REGISTRATION_FUTURE).get();
            if (registrationFuture != null && !registrationFuture.isDone()) {
                registrationFuture.completeExceptionally(cause);
            }
            ctx.close();
        }
    }
}
