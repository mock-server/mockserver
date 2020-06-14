package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallback extends Action<HttpObjectCallback> {
    private int hashCode;
    private String clientId;
    private Boolean responseCallback;
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
        this.hashCode = 0;
        return this;
    }

    public Boolean getResponseCallback() {
        return responseCallback;
    }

    public HttpObjectCallback withResponseCallback(Boolean responseCallback) {
        this.responseCallback = responseCallback;
        this.hashCode = 0;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public HttpObjectCallback withActionType(Type actionType) {
        this.actionType = actionType;
        this.hashCode = 0;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return actionType;
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
        HttpObjectCallback that = (HttpObjectCallback) o;
        return Objects.equals(clientId, that.clientId) &&
            Objects.equals(responseCallback, that.responseCallback) &&
            actionType == that.actionType;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), clientId, responseCallback, actionType);
        }
        return hashCode;
    }
}
