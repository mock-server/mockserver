package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpForward;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpForwardDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpForward> {
    private String host;
    private Integer port;
    private HttpForward.Scheme scheme;

    public HttpForwardDTO(HttpForward httpForward) {
        if (httpForward != null) {
            host = httpForward.getHost();
            port = httpForward.getPort();
            scheme = httpForward.getScheme();
        }
    }

    public HttpForwardDTO() {
    }

    public HttpForward buildObject() {
        return new HttpForward()
            .withHost(host)
            .withPort(port != null ? port : 80)
            .withScheme((scheme != null ? scheme : HttpForward.Scheme.HTTP));
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
}

