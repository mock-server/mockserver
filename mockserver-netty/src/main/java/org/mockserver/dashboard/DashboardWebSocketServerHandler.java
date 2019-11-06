package org.mockserver.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.LogEventJsonSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.ui.MockServerLogListener;
import org.mockserver.ui.MockServerMatcherListener;
import org.mockserver.ui.model.ValueWithKey;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.net.HttpHeaders.HOST;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class DashboardWebSocketServerHandler extends ChannelInboundHandlerAdapter implements MockServerLogListener, MockServerMatcherListener {

    private static final AttributeKey<Boolean> CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET = AttributeKey.valueOf("CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET");
    private static final String UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI = "/_mockserver_ui_websocket";
    private final MockServerLogger mockServerLogger;
    private final HttpStateHandler httpStateHandler;
    private WebSocketServerHandshaker handshaker;
    private CircularHashMap<ChannelHandlerContext, HttpRequest> clientRegistry = new CircularHashMap<>(100);
    private HttpRequestSerializer httpRequestSerializer;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private MockServerMatcher mockServerMatcher;
    private MockServerEventLog mockServerLog;
    private LogEventJsonSerializer logEventJsonSerializer;

    public DashboardWebSocketServerHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        this.mockServerMatcher = httpStateHandler.getMockServerMatcher();
        this.mockServerLog = httpStateHandler.getMockServerLog();
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
        this.logEventJsonSerializer = new LogEventJsonSerializer(mockServerLogger);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            if (msg instanceof FullHttpRequest && ((FullHttpRequest) msg).uri().equals(UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI)) {
                upgradeChannel(ctx, (FullHttpRequest) msg);
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).set(true);
            } else if (ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).get() != null &&
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET).get() &&
                msg instanceof WebSocketFrame) {
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void upgradeChannel(final ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        if (mockServerLog == null) {
            mockServerLog = httpStateHandler.getMockServerLog();
            mockServerLog.registerListener(this);
            mockServerMatcher = httpStateHandler.getMockServerMatcher();
            mockServerMatcher.registerListener(this);
        }
        handshaker = new WebSocketServerHandshakerFactory(
            "ws://" + httpRequest.headers().get(HOST) + UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI,
            null,
            true,
            Integer.MAX_VALUE
        ).newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(
                ctx.channel(),
                httpRequest,
                new DefaultHttpHeaders(),
                ctx.channel().newPromise()
            ).addListener((ChannelFutureListener) future -> clientRegistry.put(ctx, request()));
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) throws JsonProcessingException {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain()).addListener((ChannelFutureListener) future -> clientRegistry.remove(ctx));
        } else if (frame instanceof TextWebSocketFrame) {
            try {
                HttpRequest httpRequest = httpRequestSerializer.deserialize(((TextWebSocketFrame) frame).text());
                clientRegistry.put(ctx, httpRequest);
                sendUpdate(httpRequest, ctx);
            } catch (IllegalArgumentException iae) {
                sendMessage(ctx, ImmutableMap.of("error", iae.getMessage()));
            }
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }
    }

    private void sendMessage(ChannelHandlerContext ctx, ImmutableMap<String, Object> message) throws JsonProcessingException {
        ctx.writeAndFlush(new TextWebSocketFrame(
            objectMapper.writeValueAsString(message)
        ));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("web socket server caught exception")
                    .setThrowable(cause)
            );
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        mockServerMatcher.unregisterListener(this);
        mockServerLog.unregisterListener(this);
        ctx.fireChannelInactive();
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        for (Map.Entry<ChannelHandlerContext, HttpRequest> registryEntry : clientRegistry.entrySet()) {
            sendUpdate(registryEntry.getValue(), registryEntry.getKey());
        }
    }

    @Override
    public void updated(MockServerMatcher mockServerMatcher) {
        for (Map.Entry<ChannelHandlerContext, HttpRequest> registryEntry : clientRegistry.entrySet()) {
            sendUpdate(registryEntry.getValue(), registryEntry.getKey());
        }
    }

    private void sendUpdate(HttpRequest httpRequest, ChannelHandlerContext channelHandlerContext) {
        try {
            sendMessage(channelHandlerContext, ImmutableMap.of(
                "activeExpectations", mockServerMatcher.retrieveActiveExpectations(httpRequest).stream().map(ValueWithKey::new).collect(Collectors.toList()),
                "recordedExpectations", mockServerLog.retrieveRecordedExpectations(httpRequest).stream().map(ValueWithKey::new).collect(Collectors.toList()),
                "recordedRequests", mockServerLog.retrieveRequestLogEntries(httpRequest).stream().map(ValueWithKey::new).collect(Collectors.toList()),
                "logMessages", mockServerLog.retrieveMessageLogEntries(httpRequest).stream().map(messageLogEntry -> new ValueWithKey(logEventJsonSerializer.serialize(messageLogEntry), messageLogEntry.key())).collect(Collectors.toList())
            ));
        } catch (JsonProcessingException jpe) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while updating UI")
                    .setThrowable(jpe)
            );
        }
    }
}
