package org.mockserver.model;

import java.util.Objects;

public class AfterAction extends ObjectWithJsonToString {
    private int hashCode;
    private HttpRequest httpRequest;
    private HttpClassCallback httpClassCallback;
    private HttpObjectCallback httpObjectCallback;
    private Delay delay;

    public static AfterAction afterAction() {
        return new AfterAction();
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public AfterAction withHttpRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            clearTargets();
        }
        this.httpRequest = httpRequest;
        this.hashCode = 0;
        return this;
    }

    public HttpClassCallback getHttpClassCallback() {
        return httpClassCallback;
    }

    public AfterAction withHttpClassCallback(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            clearTargets();
        }
        this.httpClassCallback = httpClassCallback;
        this.hashCode = 0;
        return this;
    }

    public HttpObjectCallback getHttpObjectCallback() {
        return httpObjectCallback;
    }

    public AfterAction withHttpObjectCallback(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            clearTargets();
        }
        this.httpObjectCallback = httpObjectCallback;
        this.hashCode = 0;
        return this;
    }

    private void clearTargets() {
        this.httpRequest = null;
        this.httpClassCallback = null;
        this.httpObjectCallback = null;
    }

    public Delay getDelay() {
        return delay;
    }

    public AfterAction withDelay(Delay delay) {
        this.delay = delay;
        this.hashCode = 0;
        return this;
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
        AfterAction that = (AfterAction) o;
        return Objects.equals(httpRequest, that.httpRequest) &&
            Objects.equals(httpClassCallback, that.httpClassCallback) &&
            Objects.equals(httpObjectCallback, that.httpObjectCallback) &&
            Objects.equals(delay, that.delay);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(httpRequest, httpClassCallback, httpObjectCallback, delay);
        }
        return hashCode;
    }
}
