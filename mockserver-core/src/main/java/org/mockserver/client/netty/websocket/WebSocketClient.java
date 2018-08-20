package org.mockserver.client.netty.websocket;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.mockserver.client.serialization.WebSocketMessageSerializer;
import org.mockserver.client.serialization.model.WebSocketClientIdDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

import static org.mockserver.callback.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;

/**
 * @author jamesdbloom
 */
public class WebSocketClient {

    private Channel channel;
    private EventLoopGroup group = new NioEventLoopGroup();
    private WebSocketMessageSerializer webSocketMessageSerializer = new WebSocketMessageSerializer(new MockServerLogger());
    private SettableFuture<String> registrationFuture = SettableFuture.create();

    private ExpectationResponseCallback expectationResponseCallback;
    private ExpectationForwardCallback expectationForwardCallback;

    public WebSocketClient(InetSocketAddress serverAddress, String contextPath) {
        try {
            final WebSocketClientHandler webSocketClientHandler = new WebSocketClientHandler(serverAddress, contextPath, this);

            channel = new Bootstrap().group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            .addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(Integer.MAX_VALUE),
                                webSocketClientHandler
                            );
                    }
                }).connect(serverAddress).sync().channel();

        } catch (Exception e) {
            throw new WebSocketException("Exception while starting web socket client", e);
        }
    }

    SettableFuture<String> registrationFuture() {
        return registrationFuture;
    }

    void receivedTextWebSocketFrame(TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            if (deserializedMessage instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) deserializedMessage;
                String webSocketCorrelationId = httpRequest.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
                if (expectationResponseCallback != null) {
                    HttpResponse httpResponse = expectationResponseCallback.handle(httpRequest);
                    httpResponse.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                    channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(httpResponse)));
                }
                if (expectationForwardCallback != null) {
                    HttpRequest forwardedHttpRequest = expectationForwardCallback.handle(httpRequest);
                    forwardedHttpRequest.withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, webSocketCorrelationId);
                    channel.writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(forwardedHttpRequest)));
                }
            } else if (!(deserializedMessage instanceof WebSocketClientIdDTO)) {
                throw new WebSocketException("Unsupported web socket message " + deserializedMessage);
            }
        } catch (Exception e) {
            throw new WebSocketException("Exception while receiving web socket message", e);
        }
    }

    public void stopClient() {
        if (!group.isShuttingDown() || group.isShutdown()) {
            group.shutdownGracefully();
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close().sync();
                channel = null;
            }
        } catch (InterruptedException e) {
            throw new WebSocketException("Exception while closing client", e);
        }
    }

    public WebSocketClient registerExpectationCallback(ExpectationResponseCallback expectationResponseCallback) {
        if (expectationForwardCallback == null) {
            this.expectationResponseCallback = expectationResponseCallback;
        } else {
            throw new IllegalArgumentException("It is not possible to set response callback once a forward callback has been set");
        }
        return this;
    }

    public WebSocketClient registerExpectationCallback(ExpectationForwardCallback expectationForwardCallback) {
        if (expectationResponseCallback == null) {
            this.expectationForwardCallback = expectationForwardCallback;
        } else {
            throw new IllegalArgumentException("It is not possible to set forward callback once a response callback has been set");
        }
        return this;
    }

    public String clientId() {
        try {
            return registrationFuture.get();
        } catch (Exception e) {
            if (e.getCause() instanceof WebSocketException && e.getCause().getMessage().contains("ExpectationResponseCallback and ExpectationForwardCallback is not supported")) {
                throw new WebSocketException(e.getCause().getMessage());
            } else {
                throw new WebSocketException("Unable to retrieve client registration id", e);
            }
        }
    }
}
