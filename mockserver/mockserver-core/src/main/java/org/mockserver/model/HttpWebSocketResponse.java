package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class HttpWebSocketResponse extends Action<HttpWebSocketResponse> {
    private int hashCode;
    private String subprotocol;
    private List<WebSocketMessage> messages;
    private Boolean closeConnection;

    public static HttpWebSocketResponse webSocketResponse() {
        return new HttpWebSocketResponse();
    }

    public HttpWebSocketResponse withSubprotocol(String subprotocol) {
        this.subprotocol = subprotocol;
        this.hashCode = 0;
        return this;
    }

    public String getSubprotocol() {
        return subprotocol;
    }

    public HttpWebSocketResponse withMessages(List<WebSocketMessage> messages) {
        this.messages = messages;
        this.hashCode = 0;
        return this;
    }

    public HttpWebSocketResponse withMessages(WebSocketMessage... messages) {
        this.messages = Arrays.asList(messages);
        this.hashCode = 0;
        return this;
    }

    public HttpWebSocketResponse withMessage(WebSocketMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.hashCode = 0;
        return this;
    }

    public List<WebSocketMessage> getMessages() {
        return messages;
    }

    public HttpWebSocketResponse withCloseConnection(Boolean closeConnection) {
        this.closeConnection = closeConnection;
        this.hashCode = 0;
        return this;
    }

    public Boolean getCloseConnection() {
        return closeConnection;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.WEBSOCKET_RESPONSE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpWebSocketResponse that = (HttpWebSocketResponse) o;
        return Objects.equals(subprotocol, that.subprotocol) &&
            Objects.equals(messages, that.messages) &&
            Objects.equals(closeConnection, that.closeConnection);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), subprotocol, messages, closeConnection);
        }
        return hashCode;
    }
}
