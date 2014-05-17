package org.mockserver.model;

import org.mockserver.client.serialization.ObjectMapperFactory;

/**
 * @author jamesdbloom
 */
public class HttpForward extends Action {

    private String host;
    private Integer port;
    private Scheme scheme = Scheme.HTTP;

    public enum Scheme {
        HTTP,
        HTTPS
    }

    public HttpForward() {
    }

    public static HttpForward forward() {
        return new HttpForward();
    }

    public String getHost() {
        return host;
    }

    public HttpForward withHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpForward withPort(Integer port) {
        this.port = port;
        return this;
    }

    public Scheme getScheme() {
        return scheme;
    }

    public HttpForward withScheme(Scheme scheme) {
        this.scheme = scheme;
        return this;
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
