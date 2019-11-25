package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequest extends Action<HttpOverrideForwardedRequest> {

    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

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

    /**
     * Static builder which will allow overriding proxied request with the specified request.
     *
     * @param httpRequest  the HttpRequest specifying what to override
     * @param httpResponse the HttpRequest specifying what to override
     */
    public static HttpOverrideForwardedRequest forwardOverriddenRequest(HttpRequest httpRequest, HttpResponse httpResponse) {
        return new HttpOverrideForwardedRequest()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * All fields, headers, cookies, etc of the provided request will be overridden
     *
     * @param httpRequest the HttpRequest specifying what to override
     */
    public HttpOverrideForwardedRequest withHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    /**
     * All fields, headers, cookies, etc of the provided response will be overridden
     *
     * @param httpResponse the HttpResponse specifying what to override
     */
    public HttpOverrideForwardedRequest withHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD_REPLACE;
    }

}
