package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.codec.MockServerHttpServerCodec;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;

public class HttpWebSocketResponseActionHandler {

    private final MockServerLogger mockServerLogger;
    private final Scheduler scheduler;

    public HttpWebSocketResponseActionHandler(MockServerLogger mockServerLogger, Scheduler scheduler) {
        this.mockServerLogger = mockServerLogger;
        this.scheduler = scheduler;
    }

    public void handle(HttpWebSocketResponse httpWebSocketResponse, ChannelHandlerContext ctx, org.mockserver.model.HttpRequest request) {
        FullHttpRequest nettyRequest = buildNettyRequest(request);
        String host = request.getFirstHeader("Host");
        String uri = request.getPath().getValue();
        String scheme = ctx.pipeline().get(SslHandler.class) != null ? "wss" : "ws";
        String wsUrl = scheme + "://" + (host != null ? host : "localhost") + uri;
        String subprotocol = httpWebSocketResponse.getSubprotocol();

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
            wsUrl, subprotocol, true, 65536
        );
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(nettyRequest);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            nettyRequest.release();
            return;
        }

        nettyRequest.retain();
        handshaker.handshake(ctx.channel(), nettyRequest).addListener(future -> {
            try {
                if (future.isSuccess()) {
                    removePipelineHandlers(ctx);

                    List<WebSocketMessage> messages = httpWebSocketResponse.getMessages();
                    if (messages != null && !messages.isEmpty()) {
                        scheduleMessages(messages, 0, ctx, httpWebSocketResponse, request, handshaker);
                    } else {
                        finishWebSocket(ctx, httpWebSocketResponse, handshaker);
                    }
                } else {
                    if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.WARN)
                                .setCorrelationId(request.getLogCorrelationId())
                                .setHttpRequest(request)
                                .setMessageFormat("WebSocket handshake failed for request:{}")
                                .setArguments(request)
                        );
                    }
                }
            } finally {
                nettyRequest.release();
            }
        });
        nettyRequest.release();
    }

    private void removePipelineHandlers(ChannelHandlerContext ctx) {
        try {
            ctx.pipeline().remove(MockServerHttpServerCodec.class);
        } catch (Exception ignored) {
        }
        try {
            for (Map.Entry<String, ChannelHandler> entry : ctx.pipeline()) {
                if ("HttpRequestHandler".equals(entry.getValue().getClass().getSimpleName())) {
                    ctx.pipeline().remove(entry.getKey());
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleMessages(List<WebSocketMessage> messages, int index, ChannelHandlerContext ctx,
                                  HttpWebSocketResponse httpWebSocketResponse, org.mockserver.model.HttpRequest request,
                                  WebSocketServerHandshaker handshaker) {
        if (index >= messages.size() || !ctx.channel().isActive()) {
            finishWebSocket(ctx, httpWebSocketResponse, handshaker);
            return;
        }

        WebSocketMessage message = messages.get(index);
        Delay delay = message.getDelay();

        Runnable writeMessage = () -> {
            try {
                if (!ctx.channel().isActive()) {
                    return;
                }

                WebSocketFrame frame;
                if (message.getBinary() != null) {
                    frame = new BinaryWebSocketFrame(Unpooled.copiedBuffer(message.getBinary()));
                } else if (message.getText() != null) {
                    frame = new TextWebSocketFrame(message.getText());
                } else {
                    scheduleMessages(messages, index + 1, ctx, httpWebSocketResponse, request, handshaker);
                    return;
                }

                ctx.writeAndFlush(frame).addListener(future -> {
                    if (future.isSuccess()) {
                        if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(EXPECTATION_RESPONSE)
                                    .setLogLevel(Level.DEBUG)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("sent WebSocket message {} of {} for request:{}")
                                    .setArguments(index + 1, messages.size(), request)
                            );
                        }
                        scheduleMessages(messages, index + 1, ctx, httpWebSocketResponse, request, handshaker);
                    } else {
                        if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.WARN)
                                    .setCorrelationId(request.getLogCorrelationId())
                                    .setHttpRequest(request)
                                    .setMessageFormat("async write failure for WebSocket message {} for request:{}")
                                    .setArguments(index + 1, request)
                                    .setThrowable(future.cause())
                            );
                        }
                        finishWebSocket(ctx, httpWebSocketResponse, handshaker);
                    }
                });
            } catch (Exception e) {
                if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setHttpRequest(request)
                            .setMessageFormat("exception sending WebSocket message {} for request:{}")
                            .setArguments(index + 1, request)
                            .setThrowable(e)
                    );
                }
                finishWebSocket(ctx, httpWebSocketResponse, handshaker);
            }
        };

        if (delay != null) {
            scheduler.schedule(writeMessage, false, delay);
        } else {
            writeMessage.run();
        }
    }

    private void finishWebSocket(ChannelHandlerContext ctx, HttpWebSocketResponse httpWebSocketResponse,
                                 WebSocketServerHandshaker handshaker) {
        if (ctx.channel().isActive()) {
            if (httpWebSocketResponse.getCloseConnection() == null || httpWebSocketResponse.getCloseConnection()) {
                handshaker.close(ctx.channel(), new CloseWebSocketFrame());
            }
        }
    }

    private FullHttpRequest buildNettyRequest(org.mockserver.model.HttpRequest request) {
        String uri = request.getPath().getValue();
        if (request.getQueryStringParameters() != null && !request.getQueryStringParameters().isEmpty()) {
            StringBuilder qs = new StringBuilder("?");
            boolean first = true;
            for (Parameter param : request.getQueryStringParameters().getEntries()) {
                for (NottableString value : param.getValues()) {
                    if (!first) {
                        qs.append("&");
                    }
                    try {
                        qs.append(URLEncoder.encode(param.getName().getValue(), "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(value.getValue(), "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        qs.append(param.getName().getValue())
                            .append("=")
                            .append(value.getValue());
                    }
                    first = false;
                }
            }
            uri = uri + qs.toString();
        }
        String method = request.getMethod() != null ? request.getMethod().getValue() : "GET";

        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.valueOf(method),
            uri
        );

        if (request.getHeaderList() != null) {
            for (org.mockserver.model.Header header : request.getHeaderList()) {
                for (NottableString value : header.getValues()) {
                    nettyRequest.headers().add(header.getName().getValue(), value.getValue());
                }
            }
        }

        if (!nettyRequest.headers().contains(HttpHeaderNames.HOST)) {
            nettyRequest.headers().set(HttpHeaderNames.HOST, "localhost");
        }

        return nettyRequest;
    }
}
