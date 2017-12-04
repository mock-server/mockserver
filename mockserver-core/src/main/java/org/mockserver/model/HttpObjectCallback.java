package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallback extends Action {

    private String clientId;

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.OBJECT_CALLBACK;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id of the web socket client that will handle the callback
     *
     * The client id must be for client with an open web socket,
     * if no client is found with id a 404 response will be returned
     *
     * @param clientId client id of the web socket client that will handle the callback
     */
    public HttpObjectCallback withClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }
}
