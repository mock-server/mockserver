package org.mockserver.serialization.model;

import org.mockserver.model.Delay;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallbackDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpObjectCallback> {

    private String clientId;
    private Boolean responseCallback;
    private DelayDTO delay;

    public HttpObjectCallbackDTO(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            clientId = httpObjectCallback.getClientId();
            responseCallback = httpObjectCallback.getResponseCallback();
            if (httpObjectCallback.getDelay() != null) {
                delay = new DelayDTO(httpObjectCallback.getDelay());
            }
        }
    }

    public HttpObjectCallbackDTO() {
    }

    public HttpObjectCallback buildObject() {
        Delay delay = null;
        if (this.delay != null) {
            delay = this.delay.buildObject();
        }
        return new HttpObjectCallback()
            .withClientId(clientId)
            .withResponseCallback(responseCallback)
            .withDelay(delay);
    }

    public String getClientId() {
        return clientId;
    }

    public HttpObjectCallbackDTO setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public Boolean getResponseCallback() {
        return responseCallback;
    }

    public HttpObjectCallbackDTO setResponseCallback(Boolean responseCallback) {
        this.responseCallback = responseCallback;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public void setDelay(DelayDTO delay) {
        this.delay = delay;
    }
}

