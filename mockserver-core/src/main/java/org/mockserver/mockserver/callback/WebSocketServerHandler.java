package org.mockserver.mockserver.callback;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.google.common.net.HttpHeaders.HOST;

/**
 * @author jamesdbloom
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    public static final String WEB_SOCKET_URI = "/_mockserver_callback_websocket";
    private WebSocketServerHandshaker handshaker;
    private WebSocketClientRegistry webSocketClientRegistry;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public WebSocketServerHandler(WebSocketClientRegistry webSocketClientRegistry) {
        this.webSocketClientRegistry = webSocketClientRegistry;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest && ((FullHttpRequest) msg).uri().equals(WEB_SOCKET_URI)) {
            upgradeChannel(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        boolean websocketHandshake = msg instanceof FullHttpRequest && ((FullHttpRequest) msg).uri().equals(WEB_SOCKET_URI);
        boolean websocketFrame = msg instanceof WebSocketFrame;
        return websocketHandshake || websocketFrame;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void upgradeChannel(final ChannelHandlerContext ctx, FullHttpRequest httpRequest) {
        handshaker = new WebSocketServerHandshakerFactory(
            "ws://" + httpRequest.headers().get(HOST) + WEB_SOCKET_URI,
            null,
            true,
            Integer.MAX_VALUE
        ).newHandshaker(httpRequest);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            final String clientId = UUID.randomUUID().toString();
            handshaker.handshake(
                ctx.channel(),
                httpRequest,
                new DefaultHttpHeaders().add("X-CLIENT-REGISTRATION-ID", clientId),
                ctx.channel().newPromise()
            ).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    webSocketClientRegistry.registerClient(clientId, ctx);
                    // TODO(jamesdbloom) remove mockserver codec and handler
                }
            });
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
        } else if (frame instanceof TextWebSocketFrame) {
            webSocketClientRegistry.receivedTextWebSocketFrame(ctx, ((TextWebSocketFrame) frame));
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("web socket server caught exception", cause);
        ctx.close();
    }

}
