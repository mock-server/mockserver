package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class WebSocketErrorDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String message;
    private String webSocketCorrelationId;

    public String getMessage() {
        return message;
    }

    public WebSocketErrorDTO setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getWebSocketCorrelationId() {
        return webSocketCorrelationId;
    }

    public WebSocketErrorDTO setWebSocketCorrelationId(String webSocketCorrelationId) {
        this.webSocketCorrelationId = webSocketCorrelationId;
        return this;
    }
}
