package org.mockserver.mockserver.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.filters.MockServerLog;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mockserver.ui.model.ValueWithKey;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.google.common.net.HttpHeaders.HOST;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class UIWebSocketServerHandler extends ChannelInboundHandlerAdapter implements MockServerLogListener, MockServerMatcherListener {

    private static final AttributeKey<Boolean> CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET = AttributeKey.valueOf("CHANNEL_UPGRADED_FOR_UI_WEB_SOCKET");
    private static final String UPGRADE_CHANNEL_FOR_UI_WEB_SOCKET_URI = "/_mockserver_ui_websocket";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private WebSocketServerHandshaker handshaker;
    private CircularHashMap<ChannelHandlerContext, HttpRequest> clientRegistry = new CircularHashMap<>(100);
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private MockServerMatcher mockServerMatcher;
    private MockServerLog mockServerLog;
    private Function<ObjectWithReflectiveEqualsHashCodeToString, Object> wrapValueWithKey = new Function<ObjectWithReflectiveEqualsHashCodeToString, Object>() {
        public ValueWithKey apply(ObjectWithReflectiveEqualsHashCodeToString input) {
            return new ValueWithKey(input);
        }
    };

    public UIWebSocketServerHandler(HttpStateHandler httpStateHandler) {
        mockServerMatcher = httpStateHandler.getMockServerMatcher();
        mockServerMatcher.registerListener(this);
        mockServerLog = httpStateHandler.getMockServerLog();
        mockServerLog.registerListener(this);
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
            ).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    clientRegistry.put(ctx, request());
                }
            });
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) throws JsonProcessingException {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain()).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    clientRegistry.remove(ctx);
                }
            });
        } else if (frame instanceof TextWebSocketFrame) {
            try {
                HttpRequest httpRequest = httpRequestSerializer.deserialize(((TextWebSocketFrame) frame).text());
                clientRegistry.put(ctx, httpRequest);
                sendUpdate(httpRequest, ctx);
            } catch (IllegalArgumentException iae) {
                sendMessage(ctx, ImmutableMap.<String, Object>of("error", iae.getMessage()));
            }
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }
    }

    private void sendMessage(ChannelHandlerContext ctx, ImmutableMap<String, Object> message) throws JsonProcessingException {
        ctx.channel().writeAndFlush(new TextWebSocketFrame(
            objectMapper.writeValueAsString(message)
        ));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("web socket server caught exception", cause);
        ctx.close();
    }

    @Override
    public void updated(MockServerLog mockServerLog) {
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
            sendMessage(channelHandlerContext, ImmutableMap.<String, Object>of(
                "activeExpectations", Lists.transform(mockServerMatcher.retrieveExpectations(httpRequest), wrapValueWithKey),
                "recordedExpectations", Lists.transform(mockServerLog.retrieveExpectations(httpRequest), wrapValueWithKey),
                "recordedRequests", Lists.transform(mockServerLog.retrieveRequestLogEntries(httpRequest), wrapValueWithKey),
                "logMessages", Lists.transform(mockServerLog.retrieveMessageLogEntries(httpRequest), new Function<MessageLogEntry, Object>() {
                    public ValueWithKey apply(MessageLogEntry input) {
                        return new ValueWithKey(input.getMessage(), input.key());
                    }
                })
            ));
        } catch (JsonProcessingException jpe) {
            logger.error("Exception while updating UI", jpe);
        }
    }
}
