
package org.mockserver.echo.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.List;
import java.util.UUID;

import static com.google.common.net.HttpHeaders.HOST;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class WebSocketServerHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<Boolean> CHANNEL_UPGRADED_FOR_CALLBACK_WEB_SOCKET = AttributeKey.valueOf("CHANNEL_UPGRADED_FOR_CALLBACK_WEB_SOCKET");
    private static final String UPGRADE_CHANNEL_FOR_CALLBACK_WEB_SOCKET_URI = "/_mockserver_callback_websocket";
    private final MockServerLogger mockServerLogger;
    private final List<String> registeredClients;
    private final List<TextWebSocketFrame> textWebSocketFrames;
    private WebSocketServerHandshaker handshaker;

    WebSocketServerHandler(MockServerLogger mockServerLogger, List<String> registeredClients, List<TextWebSocketFrame> textWebSocketFrames) {
        this.mockServerLogger = mockServerLogger;
        this.registeredClients = registeredClients;
        this.textWebSocketFrames = textWebSocketFrames;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        boolean release = true;
        try {
            if (msg instanceof FullHttpRequest && ((FullHttpRequest) msg).uri().equals(UPGRADE_CHANNEL_FOR_CALLBACK_WEB_SOCKET_URI)) {
                upgradeChannel(ctx, (FullHttpRequest) msg);
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_CALLBACK_WEB_SOCKET).set(true);
            } else if (ctx.channel().attr(CHANNEL_UPGRADED_FOR_CALLBACK_WEB_SOCKET).get() != null &&
                ctx.channel().attr(CHANNEL_UPGRADED_FOR_CALLBACK_WEB_SOCKET).get() &&
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
            "ws://" + httpRequest.headers().get(HOST) + UPGRADE_CHANNEL_FOR_CALLBACK_WEB_SOCKET_URI,
            null,
            true,
            Integer.MAX_VALUE
        ).newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            final String clientId = UUID.randomUUID().toString();
            handshaker
                .handshake(
                    ctx.channel(),
                    httpRequest,
                    new DefaultHttpHeaders().add("X-CLIENT-REGISTRATION-ID", clientId),
                    ctx.channel().newPromise()
                )
                .addListener((ChannelFutureListener) future -> {
                    ctx.pipeline().remove(MockServerServerCodec.class);
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.TRACE)
                            .setLogLevel(Level.TRACE)
                            .setMessageFormat("Registering client " + clientId)
                    );
                    registeredClients.add(clientId);
                    future.channel().closeFuture().addListener((ChannelFutureListener) future1 -> {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(LogEntry.LogMessageType.TRACE)
                                .setLogLevel(Level.TRACE)
                                .setMessageFormat("Unregistering callback for client " + clientId)
                        );
                        registeredClients.remove(clientId);
                    });
                });
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
        } else if (frame instanceof TextWebSocketFrame) {
            textWebSocketFrames.add(((TextWebSocketFrame) frame));
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
        } else {
            throw new UnsupportedOperationException(frame.getClass().getName() + " frame types not supported");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(LogEntry.LogMessageType.EXCEPTION)
                .setLogLevel(Level.ERROR)
                .setMessageFormat("web socket server caught exception")
                .setThrowable(cause)
        );
        ctx.close();
    }

}
