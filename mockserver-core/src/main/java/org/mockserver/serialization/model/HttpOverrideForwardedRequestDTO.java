package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("UnusedReturnValue")
public class HttpOverrideForwardedRequestDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpOverrideForwardedRequest> {

    @JsonAlias("httpRequest")
    private HttpRequestDTO requestOverride;
    private HttpRequestModifierDTO requestModifier;
    @JsonAlias("httpResponse")
    private HttpResponseDTO responseOverride;
    private HttpResponseModifierDTO responseModifier;
    private DelayDTO delay;

    public HttpOverrideForwardedRequestDTO(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        if (httpOverrideForwardedRequest != null) {
            HttpRequest overrideHttpRequest = httpOverrideForwardedRequest.getRequestOverride();
            if (overrideHttpRequest != null) {
                this.requestOverride = new HttpRequestDTO(overrideHttpRequest);
            }
            HttpRequestModifier modifyHttpRequest = httpOverrideForwardedRequest.getRequestModifier();
            if (modifyHttpRequest != null) {
                this.requestModifier = new HttpRequestModifierDTO(modifyHttpRequest);
            }
            HttpResponse overrideHttpResponse = httpOverrideForwardedRequest.getResponseOverride();
            if (overrideHttpResponse != null) {
                this.responseOverride = new HttpResponseDTO(overrideHttpResponse);
            }
            HttpResponseModifier modifyHttpResponse = httpOverrideForwardedRequest.getResponseModifier();
            if (modifyHttpResponse != null) {
                this.responseModifier = new HttpResponseModifierDTO(modifyHttpResponse);
            }
            delay = (httpOverrideForwardedRequest.getDelay() != null ? new DelayDTO(httpOverrideForwardedRequest.getDelay()) : null);
        }
    }

    public HttpOverrideForwardedRequestDTO() {
    }

    public HttpOverrideForwardedRequest buildObject() {
        HttpRequest overrideHttpRequest = null;
        if (this.requestOverride != null) {
            overrideHttpRequest = this.requestOverride.buildObject();
        }
        HttpRequestModifier modifyHttpRequest = null;
        if (this.requestModifier != null) {
            modifyHttpRequest = this.requestModifier.buildObject();
        }
        HttpResponse overrideHttpResponse = null;
        if (this.responseOverride != null) {
            overrideHttpResponse = this.responseOverride.buildObject();
        }
        HttpResponseModifier modifyHttpResponse = null;
        if (this.responseModifier != null) {
            modifyHttpResponse = this.responseModifier.buildObject();
        }
        return new HttpOverrideForwardedRequest()
            .withRequestOverride(overrideHttpRequest)
            .withRequestModifier(modifyHttpRequest)
            .withResponseOverride(overrideHttpResponse)
            .withResponseModifier(modifyHttpResponse)
            .withDelay((delay != null ? delay.buildObject() : null));
    }

    public HttpRequestDTO getRequestOverride() {
        return requestOverride;
    }

    public HttpOverrideForwardedRequestDTO setRequestOverride(HttpRequestDTO requestOverride) {
        this.requestOverride = requestOverride;
        return this;
    }

    public HttpRequestModifierDTO getRequestModifier() {
        return requestModifier;
    }

    public HttpOverrideForwardedRequestDTO setRequestModifier(HttpRequestModifierDTO requestModifier) {
        this.requestModifier = requestModifier;
        return this;
    }

    public HttpResponseDTO getResponseOverride() {
        return responseOverride;
    }

    public HttpOverrideForwardedRequestDTO setResponseOverride(HttpResponseDTO responseOverride) {
        this.responseOverride = responseOverride;
        return this;
    }

    public HttpResponseModifierDTO getResponseModifier() {
        return responseModifier;
    }

    public HttpOverrideForwardedRequestDTO setResponseModifier(HttpResponseModifierDTO responseModifier) {
        this.responseModifier = responseModifier;
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

