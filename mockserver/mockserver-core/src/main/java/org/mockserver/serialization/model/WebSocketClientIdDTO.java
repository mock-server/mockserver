package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class WebSocketClientIdDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public WebSocketClientIdDTO setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }
}
