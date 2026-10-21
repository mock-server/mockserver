package org.mockserver.model;

import java.util.Objects;

public class AfterAction extends ObjectWithJsonToString {
    private int hashCode;
    private HttpRequest httpRequest;
    private HttpTemplate httpRequestTemplate;
    private HttpClassCallback httpClassCallback;
    private HttpObjectCallback httpObjectCallback;
    private Delay delay;
    private Boolean blocking;
    private Delay timeout;
    private FailurePolicy failurePolicy;

    public static AfterAction afterAction() {
        return new AfterAction();
    }

    /**
     * Convenience factory producing an action intended for use as a before-action.
     * Identical to {@link #afterAction()}; the same type backs both
     * {@code beforeActions} and {@code afterActions}.
     */
    public static AfterAction beforeAction() {
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

    /**
     * A webhook whose outbound request is rendered by a Velocity, Mustache or JavaScript template, which sees the
     * triggering request as {@code request}. The template must produce a request in MockServer's JSON format. This
     * action's {@link #getDelay() delay} applies; the template's own delay, primary, responseOverride and
     * responseModifier are ignored. Escape request values (e.g. Velocity {@code $esc.java}) and keep them out of the
     * target (Host, socketAddress, port): unescaped request data can change where the webhook, and its headers, go.
     */
    public HttpTemplate getHttpRequestTemplate() {
        return httpRequestTemplate;
    }

    public AfterAction withHttpRequestTemplate(HttpTemplate httpRequestTemplate) {
        if (httpRequestTemplate != null) {
            clearTargets();
        }
        this.httpRequestTemplate = httpRequestTemplate;
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
        this.httpRequestTemplate = null;
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

    /**
     * Before-actions only: when {@code true} (the default when unset) the primary response
     * waits for this action to complete before being written; when {@code false} the action is
     * started before the response but not waited for. Ignored for after-actions.
     */
    public Boolean getBlocking() {
        return blocking;
    }

    public AfterAction withBlocking(Boolean blocking) {
        this.blocking = blocking;
        this.hashCode = 0;
        return this;
    }

    /**
     * Before-actions only: maximum time to wait for a blocking action to complete. When the
     * timeout elapses the action is treated as failed (see {@link #getFailurePolicy()}). When
     * unset only the underlying client's socket timeout applies. Ignored for after-actions.
     */
    public Delay getTimeout() {
        return timeout;
    }

    public AfterAction withTimeout(Delay timeout) {
        this.timeout = timeout;
        this.hashCode = 0;
        return this;
    }

    /**
     * Before-actions only: what to do with the primary response when a blocking action fails or
     * times out. Defaults to {@link FailurePolicy#BEST_EFFORT} when unset. Ignored for
     * after-actions.
     */
    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public AfterAction withFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
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
            Objects.equals(httpRequestTemplate, that.httpRequestTemplate) &&
            Objects.equals(httpClassCallback, that.httpClassCallback) &&
            Objects.equals(httpObjectCallback, that.httpObjectCallback) &&
            Objects.equals(delay, that.delay) &&
            Objects.equals(blocking, that.blocking) &&
            Objects.equals(timeout, that.timeout) &&
            failurePolicy == that.failurePolicy;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(httpRequest, httpRequestTemplate, httpClassCallback, httpObjectCallback, delay, blocking, timeout, failurePolicy);
        }
        return hashCode;
    }
}
