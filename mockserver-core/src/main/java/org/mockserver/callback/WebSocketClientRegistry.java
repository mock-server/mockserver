package org.mockserver.callback;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.mockserver.client.netty.websocket.WebSocketException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.WebSocketClientIdDTO;
import org.mockserver.collections.CircularHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.mockserver.configuration.ConfigurationProperties.maxWebSocketExpectations;

/**
 * @author jamesdbloom
 */
public class WebSocketClientRegistry {

    public static final String WEB_SOCKET_CORRELATION_ID_HEADER_NAME = "WebSocketCorrelationId";
    private WebSocketMessageSerializer webSocketMessageSerializer = new WebSocketMessageSerializer(new MockServerLogger());
    private CircularHashMap<String, ChannelHandlerContext> clientRegistry = new CircularHashMap<>(maxWebSocketExpectations());
    private CircularHashMap<String, WebSocketResponseCallback> callbackResponseRegistry = new CircularHashMap<>(maxWebSocketExpectations());
    private CircularHashMap<String, WebSocketRequestCallback> callbackForwardRegistry = new CircularHashMap<>(maxWebSocketExpectations());

    void receivedTextWebSocketFrame(TextWebSocketFrame textWebSocketFrame) {
        try {
            Object deserializedMessage = webSocketMessageSerializer.deserialize(textWebSocketFrame.text());
            if (deserializedMessage instanceof HttpResponse) {
                HttpResponse httpResponse = (HttpResponse) deserializedMessage;
                String firstHeader = httpResponse.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME);
                WebSocketResponseCallback webSocketResponseCallback = callbackResponseRegistry.get(firstHeader);
                if (webSocketResponseCallback != null) {
                    webSocketResponseCallback.handle(httpResponse);
                }
            } else if (deserializedMessage instanceof HttpRequest) {
                HttpRequest httpRequest = (HttpRequest) deserializedMessage;
                WebSocketRequestCallback webSocketRequestCallback = callbackForwardRegistry.get(httpRequest.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME));
                if (webSocketRequestCallback != null) {
                    webSocketRequestCallback.handle(httpRequest);
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

    public void registerCallbackHandler(String webSocketCorrelationId, WebSocketResponseCallback expectationResponseCallback) {
        callbackResponseRegistry.put(webSocketCorrelationId, expectationResponseCallback);
    }

    public void registerCallbackHandler(String webSocketCorrelationId, WebSocketRequestCallback expectationForwardCallback) {
        callbackForwardRegistry.put(webSocketCorrelationId, expectationForwardCallback);
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
