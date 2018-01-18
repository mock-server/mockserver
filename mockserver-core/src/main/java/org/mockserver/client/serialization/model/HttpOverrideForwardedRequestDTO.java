package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpOverrideForwardedRequest> {

    private HttpRequestDTO httpRequest;
    private DelayDTO delay;

    public HttpOverrideForwardedRequestDTO(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        if (httpOverrideForwardedRequest != null) {
            HttpRequest httpRequest = httpOverrideForwardedRequest.getHttpRequest();
            if (httpRequest != null) {
                this.httpRequest = new HttpRequestDTO(httpRequest, httpRequest.getNot());
            }
            delay = (httpOverrideForwardedRequest.getDelay() != null ? new DelayDTO(httpOverrideForwardedRequest.getDelay()) : null);
        }
    }

    public HttpOverrideForwardedRequestDTO() {
    }

    public HttpOverrideForwardedRequest buildObject() {
        HttpRequest httpRequest = null;
        if (this.httpRequest != null) {
            httpRequest = this.httpRequest.buildObject();
        }
        return new HttpOverrideForwardedRequest()
            .withHttpRequest(httpRequest)
            .withDelay((delay != null ? delay.buildObject() : null));
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public HttpOverrideForwardedRequestDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpOverrideForwardedRequestDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}

