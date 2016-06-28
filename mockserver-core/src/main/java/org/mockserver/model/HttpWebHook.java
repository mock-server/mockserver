package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHook extends Action {
    private HttpWebHookConfig httpWebHookConfig;
    @JsonIgnore
    private HttpResponse httpResponse;

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.RESPONSE_AND_WEBHOOKS;
    }

    public HttpWebHookConfig getHttpWebHookConfig() {
        return httpWebHookConfig;
    }

    public void setHttpWebHookConfig(HttpWebHookConfig httpWebHookConfig) {
        this.httpWebHookConfig = httpWebHookConfig;
    }

    public HttpWebHook withHttpWebHookConfig(HttpWebHookConfig callbackConfig) {
        this.httpWebHookConfig = callbackConfig;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public HttpWebHook withHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpWebHook applyHttpResponseDelay() {
        httpResponse.applyDelay();
        return this;
    }
}
