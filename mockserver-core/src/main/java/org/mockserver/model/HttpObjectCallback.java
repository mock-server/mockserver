package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallback extends Action<HttpObjectCallback> {

    private String clientId;
    private Type actionType;

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id of the web socket client that will handle the callback
     * <p>
     * The client id must be for client with an open web socket,
     * if no client is found with id a 404 response will be returned
     *
     * @param clientId client id of the web socket client that will handle the callback
     */
    public HttpObjectCallback withClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public HttpObjectCallback withActionType(Type actionType) {
        this.actionType = actionType;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return actionType;
    }
}
