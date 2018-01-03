package org.mockserver.mockserver.callback;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.mockserver.client.netty.websocket.WebSocketException;
import org.mockserver.client.serialization.WebSocketMessageSerializer;
import org.mockserver.client.serialization.model.WebSocketClientIdDTO;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.filters.MockServerLog;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class WebSocketClientRegistry {

    private WebSocketMessageSerializer webSocketMessageSerializer = new WebSocketMessageSerializer();
    private CircularHashMap<String, ChannelHandlerContext> clientRegistry = new CircularHashMap<>(100);
    private CircularHashMap<String, ExpectationCallbackResponse> callbackResponseRegistry = new CircularHashMap<>(100);

    void receivedTextWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            if (deserializedMessage instanceof HttpResponse) {
                String key = clientRegistry.findKey(ctx);
                if (key != null) {
                    callbackResponseRegistry.get(key).handle((HttpResponse) deserializedMessage);
                }
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

    public void registerCallbackResponseHandler(String clientId, ExpectationCallbackResponse expectationCallbackResponse) {
        callbackResponseRegistry.put(clientId, expectationCallbackResponse);
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
