package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequest extends Action {

    private HttpRequest httpRequest;
    private Delay delay;

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

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public HttpOverrideForwardedRequest withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value    a the number of time units to delay the response
     */
    public HttpOverrideForwardedRequest withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD_REPLACE;
    }

}
