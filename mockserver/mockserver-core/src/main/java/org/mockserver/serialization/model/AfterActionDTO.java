package org.mockserver.serialization.model;

import org.mockserver.model.*;

public class AfterActionDTO extends ObjectWithJsonToString implements DTO<AfterAction> {
    private HttpRequestDTO httpRequest;
    private HttpClassCallbackDTO httpClassCallback;
    private HttpObjectCallbackDTO httpObjectCallback;
    private DelayDTO delay;

    public AfterActionDTO() {
    }

    public AfterActionDTO(AfterAction afterAction) {
        if (afterAction != null) {
            if (afterAction.getHttpRequest() != null) {
                this.httpRequest = new HttpRequestDTO(afterAction.getHttpRequest());
            }
            if (afterAction.getHttpClassCallback() != null) {
                this.httpClassCallback = new HttpClassCallbackDTO(afterAction.getHttpClassCallback());
            }
            if (afterAction.getHttpObjectCallback() != null) {
                this.httpObjectCallback = new HttpObjectCallbackDTO(afterAction.getHttpObjectCallback());
            }
            if (afterAction.getDelay() != null) {
                this.delay = new DelayDTO(afterAction.getDelay());
            }
        }
    }

    @Override
    public AfterAction buildObject() {
        AfterAction afterAction = new AfterAction();
        if (httpRequest != null) {
            afterAction.withHttpRequest(httpRequest.buildObject());
        }
        if (httpClassCallback != null) {
            afterAction.withHttpClassCallback(httpClassCallback.buildObject());
        }
        if (httpObjectCallback != null) {
            afterAction.withHttpObjectCallback(httpObjectCallback.buildObject());
        }
        if (delay != null) {
            afterAction.withDelay(delay.buildObject());
        }
        return afterAction;
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public AfterActionDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpClassCallbackDTO getHttpClassCallback() {
        return httpClassCallback;
    }

    public AfterActionDTO setHttpClassCallback(HttpClassCallbackDTO httpClassCallback) {
        this.httpClassCallback = httpClassCallback;
        return this;
    }

    public HttpObjectCallbackDTO getHttpObjectCallback() {
        return httpObjectCallback;
    }

    public AfterActionDTO setHttpObjectCallback(HttpObjectCallbackDTO httpObjectCallback) {
        this.httpObjectCallback = httpObjectCallback;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public AfterActionDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}
