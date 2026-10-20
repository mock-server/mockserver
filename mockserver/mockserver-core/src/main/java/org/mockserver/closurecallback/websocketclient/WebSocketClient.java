package org.mockserver.closurecallback.websocketclient;

import com.google.common.base.Suppliers;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.HttpMessage;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.WebSocketClientIdDTO;
import org.mockserver.serialization.model.WebSocketErrorDTO;
import org.mockserver.socket.NettyAllocator;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.configuration.Configuration.configuration;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class WebSocketClient<T extends HttpMessage> {

    static final AttributeKey<CompletableFuture<String>> REGISTRATION_FUTURE = AttributeKey.valueOf("REGISTRATION_FUTURE");
    private final MockServerLogger mockServerLogger;
    private Channel channel;
    private final WebSocketMessageSerializer webSocketMessageSerializer;
    private ExpectationCallback<T> expectationCallback;
    private ExpectationForwardAndResponseCallback expectationForwardResponseCallback;
    private boolean isStopped = false;
    private final EventLoopGroup eventLoopGroup;
    private final String clientId;
    private final Supplier<SslContext> sslContextSupplier;
    private volatile Supplier<String> authorizationSupplier;
    public static final String CLIENT_REGISTRATION_ID_HEADER = "X-CLIENT-REGISTRATION-ID";

    /**
     * A client that, over TLS, verifies MockServer's certificate as {@code MockServerClient} does for the
     * global configuration. Its TLS context factory is built on the first secure connection and reused.
     */
    public WebSocketClient(final EventLoopGroup eventLoopGroup, final String clientId, final MockServerLogger mockServerLogger) {
        this(eventLoopGroup, clientId, mockServerLogger, mockServerClientSslContext(mockServerLogger));
    }

    private static Supplier<SslContext> mockServerClientSslContext(final MockServerLogger mockServerLogger) {
        Supplier<NettySslContextFactory> factory = Suppliers.memoize(() -> NettySslContextFactory.forMockServerClient(configuration(), mockServerLogger));
        return () -> factory.get().createClientSslContext(false, false);
    }

    /**
     * @param sslContextSupplier the TLS context for a secure connection, which decides which server certificates
     *                           are trusted and which client certificate is presented
     */
    public WebSocketClient(final EventLoopGroup eventLoopGroup, final String clientId, final MockServerLogger mockServerLogger, final Supplier<SslContext> sslContextSupplier) {
        this.eventLoopGroup = eventLoopGroup;
        this.clientId = clientId;
        this.mockServerLogger = mockServerLogger;
        this.webSocketMessageSerializer = new WebSocketMessageSerializer(mockServerLogger);
        this.sslContextSupplier = sslContextSupplier;
    }

    private String authorization() {
        Supplier<String> supplier = authorizationSupplier;
        return supplier != null ? supplier.get() : null;
    }

    /**
     * @param authorizationSupplier the {@code Authorization} header value sent on each WebSocket upgrade, so the
     *                              upgrade passes control-plane authentication; a null or blank value sends none
     */
    public WebSocketClient<T> withAuthorization(Supplier<String> authorizationSupplier) {
        this.authorizationSupplier = authorizationSupplier;
        return this;
    }

    private Future<String> register(final InetSocketAddress serverAddress, final String contextPath, final boolean isSecure, int reconnectAttempts) {
        CompletableFuture<String> registrationFuture = new CompletableFuture<>();
        try {
            final SslContext sslContext = isSecure ? sslContextSupplier.get() : null;
            final String authorization = authorization();
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
                        ch.pipeline().addLast(new WebSocketClientHandler(mockServerLogger, clientId, serverAddress, contextPath, WebSocketClient.this, isSecure, authorization));
                        // add logging
                        if (mockServerLogger.isEnabledForInstance(TRACE)) {
                            ch.pipeline().addLast(new LoggingHandler(WebSocketClient.class.getName() + "-last"));
                        }
                    }
                })
                .connect(serverAddress)
                .addListener((ChannelFutureListener) connectChannelFuture -> {
                    channel = connectChannelFuture.channel();
                    channel.closeFuture().addListener((ChannelFutureListener) closeChannelFuture -> {
                        if (!isStopped && reconnectAttempts > 0) {
                            // attempt to re-connect
                            register(serverAddress, contextPath, isSecure, reconnectAttempts - 1);
                        }
                    });
                });

            // handle HttpResponseStatus.RESET_CONTENT

        } catch (Exception e) {
            registrationFuture.completeExceptionally(new WebSocketException("Exception while starting web socket client", e));
        }
        return registrationFuture;
    }

    void receivedTextWebSocketFrame(TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            if (deserializedMessage instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) deserializedMessage;
                String webSocketCorrelationId = request.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setHttpRequest(request)
                            .setMessageFormat("received request{}over websocket for client " + clientId + " for correlationId " + webSocketCorrelationId)
                            .setArguments(request)
                    );
                }
                if (expectationCallback != null) {
                    try {
                        T result = expectationCallback.handle(request);
                        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setHttpRequest(request)
                                    .setMessageFormat("returning{}for request{}over websocket for client " + clientId + " for correlationId " + webSocketCorrelationId)
                                    .setArguments(result, request)
                            );
                        }
                        result.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(result)));
                    } catch (Throwable throwable) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.ERROR)
                                .setHttpRequest(request)
                                .setMessageFormat("exception thrown while handling callback for request - " + throwable.getMessage())
                                .setThrowable(throwable)
                        );
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(
                            new WebSocketErrorDTO()
                                .setMessage(throwable.getMessage())
                                .setWebSocketCorrelationId(webSocketCorrelationId)
                        )));
                    }
                }
            } else if (deserializedMessage instanceof HttpRequestAndHttpResponse) {
                HttpRequestAndHttpResponse httpRequestAndHttpResponse = (HttpRequestAndHttpResponse) deserializedMessage;
                HttpRequest httpRequest = httpRequestAndHttpResponse.getHttpRequest();
                HttpResponse httpResponse = httpRequestAndHttpResponse.getHttpResponse();
                String webSocketCorrelationId = httpRequest.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setHttpRequest(httpRequestAndHttpResponse.getHttpRequest())
                            .setMessageFormat("received request and response{}over websocket for client " + clientId + " for correlationId " + webSocketCorrelationId)
                            .setArguments(httpRequestAndHttpResponse)
                    );
                }
                if (expectationForwardResponseCallback != null) {
                    try {
                        HttpResponse response = expectationForwardResponseCallback.handle(httpRequest, httpResponse);
                        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setHttpRequest(httpRequestAndHttpResponse.getHttpRequest())
                                    .setMessageFormat("returning response{}for request and response{}over websocket for client " + clientId + " for correlationId " + webSocketCorrelationId)
                                    .setArguments(response, httpRequestAndHttpResponse)
                            );
                        }
                        response.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(response)));
                    } catch (Throwable throwable) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.ERROR)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("exception thrown while handling callback for request and response - " + throwable.getMessage())
                                .setThrowable(throwable)
                        );
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(
                            new WebSocketErrorDTO()
                                .setMessage(throwable.getMessage())
                                .setWebSocketCorrelationId(webSocketCorrelationId)
                        )));
                    }
                }
            } else if (deserializedMessage instanceof WebSocketClientIdDTO) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("received client id{}")
                            .setArguments(deserializedMessage)
                    );
                }
            } else {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("web socket client received a message that isn't HttpRequest or HttpRequestAndHttpResponse{} which has been deserialized as{}")
                            .setArguments(textWebSocketFrame.text(), deserializedMessage)
                    );
                }
                throw new WebSocketException("Unsupported web socket message " + textWebSocketFrame.text());
            }
        } catch (Exception e) {
            throw new WebSocketException("Exception while receiving web socket message", e);
        }
    }

    public void stopClient() {
        isStopped = true;
        try {
            if (eventLoopGroup != null && !eventLoopGroup.isShuttingDown()) {
                eventLoopGroup.shutdownGracefully();
            }
            if (channel != null && channel.isOpen()) {
                channel.close().sync();
                channel = null;
            }
        } catch (InterruptedException e) {
            throw new WebSocketException("Exception while closing client", e);
        }
    }

    public Future<String> registerExpectationCallback(final ExpectationCallback<T> expectationCallback, ExpectationForwardAndResponseCallback expectationForwardResponseCallback, final InetSocketAddress serverAddress, final String contextPath, final boolean isSecure) {
        if (this.expectationCallback == null) {
            this.expectationCallback = expectationCallback;
            this.expectationForwardResponseCallback = expectationForwardResponseCallback;
            return register(serverAddress, contextPath, isSecure, 3);
        } else {
            throw new IllegalArgumentException("It is not possible to set response callback once a forward callback has been set");
        }
    }
}
