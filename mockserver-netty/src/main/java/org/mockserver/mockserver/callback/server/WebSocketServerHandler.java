package org.mockserver.mockserver.callback.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author jamesdbloom
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;

    private static String getWebSocketLocation(FullHttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaders.Names.HOST) + "/websocket";
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if ("/websocket".equals(req.getUri())) {
            WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true);
            handshaker = wsFactory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                handshaker.handshake(ctx.channel(), req);
            }
        } else if ("/".equals(req.getUri())) {
            ByteBuf content = getContent(getWebSocketLocation(req));
            FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

            res.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
            HttpHeaders.setContentLength(res, content.readableBytes());

            ChannelFuture f = ctx.channel().writeAndFlush(res);
            if (!HttpHeaders.isKeepAlive(req)) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.channel()
                    .writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleWebSocketFrame(final ChannelHandlerContext ctx, WebSocketFrame frame) {

        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
        } else if (frame instanceof TextWebSocketFrame) {
            String request = ((TextWebSocketFrame) frame).text();
            if (request.startsWith("connect")) {
                final String id = StringUtils.substringAfter(request, "connect");
                System.out.println("Received connection from: " + id);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 1; i <= 10; i++) {
                            ctx.channel().writeAndFlush(new TextWebSocketFrame("sending message " + i + " to " + id));
                            try {
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        ctx.channel().writeAndFlush(new CloseWebSocketFrame());
                    }
                }).start();
            }
        } else {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public static ByteBuf getContent(String webSocketLocation) {
        return Unpooled.copiedBuffer("" +
                "<html>" + "\r\n" +
                "<head>" + "\r\n" +
                "<title>Web Socket JS Client</title>" + "\r\n" +
                "</head>" + "\r\n" +
                "<body>" + "\r\n" +
                "<script type=\"text/javascript\">" + "\r\n" +
                "var socket;" + "\r\n" +
                "if (!window.WebSocket) {" + "\r\n" +
                "  window.WebSocket = window.MozWebSocket;" + "\r\n" +
                "}" + "\r\n" +
                "\r\n" +
                "if (window.WebSocket) {" + "\r\n" +
                "  socket = new WebSocket(\"" + webSocketLocation + "\");" + "\r\n" +
                "  socket.onmessage = function(event) {" + "\r\n" +
                "    var messageBox = document.getElementById('messageBox');" + "\r\n" +
                "    messageBox.value = messageBox.value + '\\n' + event.data" + "\r\n" +
                "  };" + "\r\n" +
                "  socket.onopen = function(event) {" + "\r\n" +
                "    var messageBox = document.getElementById('messageBox');" + "\r\n" +
                "    messageBox.value = \"Web Socket opened!\";" + "\r\n" +
                "    send(\"connect\" + createUUID());" + "\r\n" +
                "  };" + "\r\n" +
                "  socket.onclose = function(event) {" + "\r\n" +
                "    var messageBox = document.getElementById('messageBox');" + "\r\n" +
                "    messageBox.value = messageBox.value + \"\\nWeb Socket closed\"; " + "\r\n" +
                "  };" + "\r\n" +
                "} else {" + "\r\n" +
                "  alert(\"Your browser does not support Web Socket.\");" + "\r\n" +
                "}" + "\r\n" +
                "\r\n" +
                "function send(message) {" + "\r\n" +
                "  if (!window.WebSocket) { return; }" + "\r\n" +
                "  if (socket.readyState == WebSocket.OPEN) {" + "\r\n" +
                "    socket.send(message);" + "\r\n" +
                "  } else {" + "\r\n" +
                "    alert(\"The socket is not open.\");" + "\r\n" +
                "  }" + "\r\n" +
                "}" + "\r\n" +
                "\r\n" +
                "function createUUID() {" + "\r\n" +
                "    var s = [];" + "\r\n" +
                "    var hexDigits = \"0123456789abcdef\";" + "\r\n" +
                "    for (var i = 0; i < 36; i++) {" + "\r\n" +
                "        s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);" + "\r\n" +
                "    }" + "\r\n" +
                "    s[14] = \"4\";  // bits 12-15 of the time_hi_and_version field to 0010" + "\r\n" +
                "    s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);  // bits 6-7 of the clock_seq_hi_and_reserved to 01" + "\r\n" +
                "    s[8] = s[13] = s[18] = s[23] = \"-\";" + "\r\n" +
                "    var uuid = s.join(\"\");" + "\r\n" +
                "    return uuid;" + "\r\n" +
                "}" + "\r\n" +
                "\r\n" +
                "</script>" + "\r\n" +
                "<textarea id=\"messageBox\" style=\"width:500px;height:300px;\"></textarea>" + "\r\n" +
                "</body>" + "\r\n" +
                "</html>" + "\r\n", CharsetUtil.US_ASCII);
    }
}
