package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequest extends Action<HttpOverrideForwardedRequest> {

    private HttpRequest httpRequest;

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
        return new HttpOverrideForwardedRequest().withHttpRequest(httpRequest);
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * All fields, headers, cookies, etc of the provided request will override
     *
     * @param httpRequest the HttpRequest specifying what to override
     */
    public HttpOverrideForwardedRequest withHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD_REPLACE;
    }

}
