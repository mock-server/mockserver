package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequest extends Action<HttpOverrideForwardedRequest> {
    private int hashCode;
    @JsonAlias("httpRequest")
    private HttpRequest requestOverride;
    private HttpRequestModifier requestModifier;
    @JsonAlias("httpResponse")
    private HttpResponse responseOverride;
    private HttpResponseModifier responseModifier;

    /**
     * Static builder which will allow overriding proxied request with the specified request.
     */
    public static HttpOverrideForwardedRequest forwardOverriddenRequest() {
        return new HttpOverrideForwardedRequest();
    }

    /**
     * Static builder which will allow overriding proxied request with the specified request.
     *
     * @param httpRequest the HttpRequest specifying what to override
     */
    public static HttpOverrideForwardedRequest forwardOverriddenRequest(HttpRequest httpRequest) {
        return new HttpOverrideForwardedRequest().withRequestOverride(httpRequest);
    }

    /**
     * Static builder which will allow overriding proxied request with the specified request.
     *
     * @param httpRequest  the HttpRequest specifying what to override
     * @param httpResponse the HttpRequest specifying what to override
     */
    public static HttpOverrideForwardedRequest forwardOverriddenRequest(HttpRequest httpRequest, HttpResponse httpResponse) {
        return new HttpOverrideForwardedRequest()
            .withRequestOverride(httpRequest)
            .withResponseOverride(httpResponse);
    }

    public HttpRequest getRequestOverride() {
        return requestOverride;
    }

    /**
     * All fields, headers, cookies, etc of the provided request will be overridden
     *
     * @param httpRequest the HttpRequest specifying what to override
     */
    public HttpOverrideForwardedRequest withRequestOverride(HttpRequest httpRequest) {
        this.requestOverride = httpRequest;
        this.hashCode = 0;
        return this;
    }

    public HttpRequestModifier getRequestModifier() {
        return requestModifier;
    }

    /**
     * Allow path, query parameters, headers and cookies to be modified
     *
     * @param modifyHttpRequest what to modify
     */
    public HttpOverrideForwardedRequest withRequestModifier(HttpRequestModifier modifyHttpRequest) {
        this.requestModifier = modifyHttpRequest;
        this.hashCode = 0;
        return this;
    }

    public HttpResponse getResponseOverride() {
        return responseOverride;
    }

    /**
     * All fields, headers, cookies, etc of the provided response will be overridden
     *
     * @param httpResponse the HttpResponse specifying what to override
     */
    public HttpOverrideForwardedRequest withResponseOverride(HttpResponse httpResponse) {
        this.responseOverride = httpResponse;
        this.hashCode = 0;
        return this;
    }

    public HttpResponseModifier getResponseModifier() {
        return responseModifier;
    }

    /**
     * Allow headers and cookies to be modified
     *
     * @param modifyHttpResponse what to modify
     */
    public HttpOverrideForwardedRequest withResponseModifier(HttpResponseModifier modifyHttpResponse) {
        this.responseModifier = modifyHttpResponse;
        this.hashCode = 0;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD_REPLACE;
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
        HttpOverrideForwardedRequest that = (HttpOverrideForwardedRequest) o;
        return Objects.equals(requestOverride, that.requestOverride) &&
            Objects.equals(requestModifier, that.requestModifier) &&
            Objects.equals(responseOverride, that.responseOverride) &&
            Objects.equals(responseModifier, that.responseModifier);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), requestOverride, requestModifier, responseOverride, responseModifier);
        }
        return hashCode;
    }
}
