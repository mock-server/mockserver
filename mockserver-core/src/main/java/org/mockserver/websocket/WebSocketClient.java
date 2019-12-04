package org.mockserver.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.HttpObject;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.WebSocketClientIdDTO;
import org.mockserver.serialization.model.WebSocketErrorDTO;
import org.slf4j.event.Level;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;

/**
 * @author jamesdbloom
 */
public class WebSocketClient<T extends HttpObject> {

    static final AttributeKey<CompletableFuture<String>> REGISTRATION_FUTURE = AttributeKey.valueOf("REGISTRATION_FUTURE");
    private final MockServerLogger mockServerLogger;
    private Channel channel;
    private WebSocketMessageSerializer webSocketMessageSerializer;
    private ExpectationCallback<T> expectationCallback;
    private boolean isStopped = false;
    private EventLoopGroup eventLoopGroup;

    public WebSocketClient(final EventLoopGroup eventLoopGroup, final MockServerLogger mockServerLogger) {
        this.eventLoopGroup = eventLoopGroup;
        this.mockServerLogger = mockServerLogger;
        this.webSocketMessageSerializer = new WebSocketMessageSerializer(mockServerLogger);
    }

    private Future<String> register(final InetSocketAddress serverAddress, final String contextPath, final boolean isSecure) {
        CompletableFuture<String> registrationFuture = new CompletableFuture<>();
        try {
            new Bootstrap()
                .group(this.eventLoopGroup)
                .channel(NioSocketChannel.class)
                .attr(REGISTRATION_FUTURE, registrationFuture)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws URISyntaxException {
                        if (isSecure) {
                            try {
                                ch.pipeline().addLast(
                                    SslContextBuilder
                                        .forClient()
                                        .sslProvider(SslProvider.JDK)
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .build()
                                        .newHandler(ch.alloc(), serverAddress.getHostName(), serverAddress.getPort())
                                );
                            } catch (SSLException e) {
                                throw new WebSocketException("Exception when configuring SSL Handler", e);
                            }
                        }

                        ch.pipeline()
                            .addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(Integer.MAX_VALUE),
                                new WebSocketClientHandler(mockServerLogger, serverAddress, contextPath, WebSocketClient.this, isSecure)
                            );
                    }
                })
                .connect(serverAddress)
                .addListener((ChannelFutureListener) connectChannelFuture -> {
                    channel = connectChannelFuture.channel();
                    channel.closeFuture().addListener((ChannelFutureListener) closeChannelFuture -> {
                        if (!isStopped) {
                            // attempt to re-connect
                            register(serverAddress, contextPath, isSecure);
                        }
                    });
                });
        } catch (Exception e) {
            registrationFuture.completeExceptionally(new WebSocketException("Exception while starting web socket client", e));
        }
        return registrationFuture;
    }

    void receivedTextWebSocketFrame(TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            if (deserializedMessage instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) deserializedMessage;
                String webSocketCorrelationId = httpRequest.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
                if (expectationCallback != null) {
                    try {
                        T result = expectationCallback.handle(httpRequest);
                        result.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(result)));
                    } catch (Throwable throwable) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(LogEntry.LogMessageType.EXCEPTION)
                                .setLogLevel(Level.ERROR)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("Exception thrown while handling callback - " + throwable.getMessage())
                                .setThrowable(throwable)
                        );
                        channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(
                            new WebSocketErrorDTO()
                                .setMessage(throwable.getMessage())
                                .setWebSocketCorrelationId(webSocketCorrelationId)
                        )));
                    }
                }
            } else if (!(deserializedMessage instanceof WebSocketClientIdDTO)) {
                throw new WebSocketException("Unsupported web socket message " + deserializedMessage);
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

    public Future<String> registerExpectationCallback(final ExpectationCallback<T> expectationCallback, final InetSocketAddress serverAddress, final String contextPath, final boolean isSecure) {
        if (this.expectationCallback == null) {
            this.expectationCallback = expectationCallback;
            return register(serverAddress, contextPath, isSecure);
        } else {
            throw new IllegalArgumentException("It is not possible to set response callback once a forward callback has been set");
        }
    }
}
