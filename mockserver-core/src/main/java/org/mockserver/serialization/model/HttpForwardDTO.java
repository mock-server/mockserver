package org.mockserver.serialization.model;

import org.mockserver.model.Delay;
import org.mockserver.model.HttpForward;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpForwardDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpForward> {
    private String host;
    private Integer port;
    private HttpForward.Scheme scheme;
    private DelayDTO delay;

    public HttpForwardDTO(HttpForward httpForward) {
        if (httpForward != null) {
            host = httpForward.getHost();
            port = httpForward.getPort();
            scheme = httpForward.getScheme();
            if (httpForward.getDelay() != null) {
                delay = new DelayDTO(httpForward.getDelay());
            }
        }
    }

    public HttpForwardDTO() {
    }

    public HttpForward buildObject() {
        Delay delay = null;
        if (this.delay != null) {
            delay = this.delay.buildObject();
        }
        return new HttpForward()
            .withHost(host)
            .withPort(port != null ? port : 80)
            .withScheme((scheme != null ? scheme : HttpForward.Scheme.HTTP))
            .withDelay(delay);
    }

    public String getHost() {
        return host;
    }

    public HttpForwardDTO setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpForwardDTO setPort(Integer port) {
        this.port = port;
        return this;
    }

    public HttpForward.Scheme getScheme() {
        return scheme;
    }

    public HttpForwardDTO setScheme(HttpForward.Scheme scheme) {
        this.scheme = scheme;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public void setDelay(DelayDTO delay) {
        this.delay = delay;
    }
}

