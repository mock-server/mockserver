package org.mockserver.mockserver.callback.server;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesbloom
 */
public class WebSocketClientRegistrationResponse extends ObjectWithReflectiveEqualsHashCodeToString {

    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public WebSocketClientRegistrationResponse withClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }
}
