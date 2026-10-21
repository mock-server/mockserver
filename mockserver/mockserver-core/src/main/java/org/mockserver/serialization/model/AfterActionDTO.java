package org.mockserver.serialization.model;

import org.mockserver.model.*;

public class AfterActionDTO extends ObjectWithJsonToString implements DTO<AfterAction> {
    private HttpRequestDTO httpRequest;
    private HttpTemplateDTO httpRequestTemplate;
    private HttpClassCallbackDTO httpClassCallback;
    private HttpObjectCallbackDTO httpObjectCallback;
    private DelayDTO delay;
    private Boolean blocking;
    private DelayDTO timeout;
    private FailurePolicy failurePolicy;

    public AfterActionDTO() {
    }

    public AfterActionDTO(AfterAction afterAction) {
        if (afterAction != null) {
            if (afterAction.getHttpRequest() != null) {
                this.httpRequest = new HttpRequestDTO(afterAction.getHttpRequest());
            }
            if (afterAction.getHttpRequestTemplate() != null) {
                this.httpRequestTemplate = new HttpTemplateDTO(afterAction.getHttpRequestTemplate());
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
            this.blocking = afterAction.getBlocking();
            if (afterAction.getTimeout() != null) {
                this.timeout = new DelayDTO(afterAction.getTimeout());
            }
            this.failurePolicy = afterAction.getFailurePolicy();
        }
    }

    @Override
    public AfterAction buildObject() {
        AfterAction afterAction = new AfterAction();
        if (httpRequest != null) {
            afterAction.withHttpRequest(httpRequest.buildObject());
        }
        if (httpRequestTemplate != null) {
            afterAction.withHttpRequestTemplate(httpRequestTemplate.buildObject());
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
        if (blocking != null) {
            afterAction.withBlocking(blocking);
        }
        if (timeout != null) {
            afterAction.withTimeout(timeout.buildObject());
        }
        if (failurePolicy != null) {
            afterAction.withFailurePolicy(failurePolicy);
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

    public HttpTemplateDTO getHttpRequestTemplate() {
        return httpRequestTemplate;
    }

    public AfterActionDTO setHttpRequestTemplate(HttpTemplateDTO httpRequestTemplate) {
        this.httpRequestTemplate = httpRequestTemplate;
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

    public Boolean getBlocking() {
        return blocking;
    }

    public AfterActionDTO setBlocking(Boolean blocking) {
        this.blocking = blocking;
        return this;
    }

    public DelayDTO getTimeout() {
        return timeout;
    }

    public AfterActionDTO setTimeout(DelayDTO timeout) {
        this.timeout = timeout;
        return this;
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public AfterActionDTO setFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
        return this;
    }
}
