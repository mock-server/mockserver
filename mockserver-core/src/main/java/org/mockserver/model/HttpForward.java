package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class HttpForward extends Action {

    private String host;
    private Integer port = 80;
    private Scheme scheme = Scheme.HTTP;
    private Delay delay;

    /**
     * Static builder to create a callback.
     */
    public static HttpForward forward() {
        return new HttpForward();
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.FORWARD;
    }

    public String getHost() {
        return host;
    }

    /**
     * The host or ip address to forward the request to i.e. "www.mock-server.com"
     *
     * @param host a hostname or ip address as a string
     */
    public HttpForward withHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * The port to forward the request to i.e. 80.  If not specified the port defaults to 80.
     *
     * @param port a port as an integer
     */
    public HttpForward withPort(Integer port) {
        this.port = port;
        return this;
    }

    public Scheme getScheme() {
        return scheme;
    }

    /**
     * The scheme to use when forwarded the request, either HTTP or HTTPS.  If not specified the scheme defaults to HTTP.
     *
     * @param scheme the scheme as a HttpForward.Scheme value
     */
    public HttpForward withScheme(Scheme scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public HttpForward withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value    a the number of time units to delay the response
     */
    public HttpForward withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    public enum Scheme {
        HTTP,
        HTTPS
    }
}
