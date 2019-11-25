package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.SocketAddress;

/**
 * @author jamesdbloom
 */
public class SocketAddressDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<SocketAddress> {
    private String host;
    private Integer port;
    private SocketAddress.Scheme scheme;

    public SocketAddressDTO(SocketAddress socketAddress) {
        if (socketAddress != null) {
            host = socketAddress.getHost();
            port = socketAddress.getPort();
            scheme = socketAddress.getScheme();
        }
    }

    public SocketAddressDTO() {
    }

    public SocketAddress buildObject() {
        return new SocketAddress()
            .withHost(host)
            .withPort(port != null ? port : 80)
            .withScheme((scheme != null ? scheme : SocketAddress.Scheme.HTTP));
    }

    public String getHost() {
        return host;
    }

    public SocketAddressDTO setHost(String host) {
        this.host = host;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public SocketAddressDTO setPort(Integer port) {
        this.port = port;
        return this;
    }

    public SocketAddress.Scheme getScheme() {
        return scheme;
    }

    public SocketAddressDTO setScheme(SocketAddress.Scheme scheme) {
        this.scheme = scheme;
        return this;
    }
}

