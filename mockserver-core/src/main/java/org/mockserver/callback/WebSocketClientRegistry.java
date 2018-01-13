package org.mockserver.callback;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.mockserver.client.netty.websocket.WebSocketException;
import org.mockserver.client.serialization.WebSocketMessageSerializer;
import org.mockserver.client.serialization.model.WebSocketClientIdDTO;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class WebSocketClientRegistry {

    private WebSocketMessageSerializer webSocketMessageSerializer = new WebSocketMessageSerializer();
    private CircularHashMap<String, ChannelHandlerContext> clientRegistry = new CircularHashMap<>(100);
    private CircularHashMap<String, WebSocketResponseCallback> callbackResponseRegistry = new CircularHashMap<>(100);
    private CircularHashMap<String, WebSocketRequestCallback> callbackForwardRegistry = new CircularHashMap<>(100);

    void receivedTextWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            String key = clientRegistry.findKey(ctx);
            if (deserializedMessage instanceof HttpResponse && callbackResponseRegistry.containsKey(key)) {
                callbackResponseRegistry.get(key).handle((HttpResponse) deserializedMessage);
            } else if (deserializedMessage instanceof HttpRequest && callbackForwardRegistry.containsKey(key)) {
                callbackForwardRegistry.get(key).handle((HttpRequest) deserializedMessage);
            } else {
                throw new WebSocketException("Unsupported web socket message " + deserializedMessage);
            }
        } catch (Exception e) {
            throw new WebSocketException("Exception while receiving web socket message" + textWebSocketFrame.text(), e);
        }
    }

    void registerClient(String clientId, ChannelHandlerContext ctx) {
        try {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(new WebSocketClientIdDTO().setClientId(clientId))));
        } catch (Exception e) {
            throw new WebSocketException("Exception while sending web socket registration client id message to client " + clientId, e);
        }
        clientRegistry.put(clientId, ctx);
    }

    public void registerCallbackHandler(String clientId, WebSocketResponseCallback expectationResponseCallback) {
        callbackResponseRegistry.put(clientId, expectationResponseCallback);
    }

    public void registerCallbackHandler(String clientId, WebSocketRequestCallback expectationForwardCallback) {
        callbackForwardRegistry.put(clientId, expectationForwardCallback);
    }

    public void sendClientMessage(String clientId, HttpRequest httpRequest) {
        try {
            if (clientRegistry.containsKey(clientId)) {
                clientRegistry.get(clientId).channel().writeAndFlush(new TextWebSocketFrame(webSocketMessageSerializer.serialize(httpRequest)));
            }
        } catch (Exception e) {
            throw new WebSocketException("Exception while sending web socket message " + httpRequest + " to client " + clientId, e);
        }
    }

}
