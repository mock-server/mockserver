package org.mockserver.closurecallback.websocketclient;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpResponseToMockServerHttpResponse;
import org.mockserver.model.MediaType;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaderValues.WEBSOCKET;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.closurecallback.websocketclient.WebSocketClient.CLIENT_REGISTRATION_ID_HEADER;
import static org.mockserver.closurecallback.websocketclient.WebSocketClient.REGISTRATION_FUTURE;
import static org.slf4j.event.Level.*;

@SuppressWarnings("rawtypes")
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClient webSocketClient;
    private final WebSocketClientHandshaker handshaker;
    private final MockServerLogger mockServerLogger;
    private final String clientId;

    WebSocketClientHandler(MockServerLogger mockServerLogger, String clientId, InetSocketAddress serverAddress, String contextPath, WebSocketClient webSocketClient, boolean isSecure) throws URISyntaxException {
        this.mockServerLogger = mockServerLogger;
        this.clientId = clientId;
        this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            new URI((isSecure ? "wss" : "ws") + "://" + serverAddress.getHostName() + ":" + serverAddress.getPort() + cleanContextPath(contextPath) + "/_mockserver_callback_websocket"),
            WebSocketVersion.V13,
            null,
            false,
            new DefaultHttpHeaders().add(CLIENT_REGISTRATION_ID_HEADER, clientId),
            Integer.MAX_VALUE
        );
        this.webSocketClient = webSocketClient;
    }

    private String cleanContextPath(String contextPath) {
        if (isNotBlank(contextPath)) {
            return (!contextPath.startsWith("/") ? "/" : "") + contextPath;
        } else {
            return "";
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMessageFormat("web socket client disconnected")
            );
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMessageFormat("web socket client handshake handler received{}")
                    .setArguments(msg)
            );
        }
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse httpResponse = (FullHttpResponse) msg;
            final CompletableFuture<String> registrationFuture = ch.attr(REGISTRATION_FUTURE).get();
            if (httpResponse.headers().contains(UPGRADE, WEBSOCKET, true) && !handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, httpResponse);
                registrationFuture.complete(clientId);
                if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("web socket client " + clientId + " connected")
                    );
                }
                // add extra logging
                if (MockServerLogger.isEnabled(TRACE)) {
                    ch.pipeline().addFirst(new LoggingHandler(WebSocketClient.class.getName() + "-first"));
                }
            } else if (httpResponse.status().equals(HttpResponseStatus.NOT_IMPLEMENTED)) {
                String message = readRequestBody(httpResponse);
                registrationFuture.completeExceptionally(new WebSocketException(message));
                if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat(message)
                    );
                }
            } else if (httpResponse.status().equals(HttpResponseStatus.RESET_CONTENT)) {
                if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("web socket client not required MockServer in same JVM as client")
                    );
                }
                registrationFuture.complete(clientId);
            } else {
                registrationFuture.completeExceptionally(new WebSocketException("handshake failure unsupported message received " + new FullHttpResponseToMockServerHttpResponse(mockServerLogger).mapFullHttpResponseToMockServerResponse(httpResponse)));
                if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("web socket client handshake handler received an unsupported FullHttpResponse message{}")
                            .setArguments(msg)
                    );
                }
            }
        } else if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                webSocketClient.receivedTextWebSocketFrame((TextWebSocketFrame) frame);
            } else if (frame instanceof PingWebSocketFrame) {
                ctx.write(new PongWebSocketFrame(frame.content().retain()));
            } else if (frame instanceof CloseWebSocketFrame) {
                if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setMessageFormat("web socket client received request to close")
                    );
                }
                ch.close();
            } else if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat("web socket client received an unsupported WebSocketFrame message{}")
                        .setArguments(msg)
                );
            }
        } else if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(WARN)
                    .setMessageFormat("web socket client received a message of unknown type{}")
                    .setArguments(msg)
            );
        }
    }

    private String readRequestBody(FullHttpResponse fullHttpResponse) {
        if (fullHttpResponse.content().readableBytes() > 0) {
            byte[] bodyBytes = new byte[fullHttpResponse.content().readableBytes()];
            fullHttpResponse.content().readBytes(bodyBytes);
            MediaType mediaType = MediaType.parse(fullHttpResponse.headers().get(CONTENT_TYPE));
            return new String(bodyBytes, mediaType.getCharsetOrDefault());
        }
        return "";
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("web socket client caught exception")
                .setThrowable(cause)
        );
        final CompletableFuture<String> registrationFuture = ctx.channel().attr(REGISTRATION_FUTURE).get();
        if (!registrationFuture.isDone()) {
            registrationFuture.completeExceptionally(cause);
        }
        ctx.close();
    }
}
