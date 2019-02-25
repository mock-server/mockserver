package org.mockserver.proxy;

import java.net.InetSocketAddress;

import static org.mockserver.configuration.ConfigurationProperties.*;

/**
 * @author jamesdbloom
 */
public class ProxyConfiguration {

    private final Type type;
    private final InetSocketAddress proxyAddress;

    private ProxyConfiguration(Type type, InetSocketAddress proxyAddress) {
        this.type = type;
        this.proxyAddress = proxyAddress;
    }

    public static ProxyConfiguration proxyConfiguration() {
        InetSocketAddress httpProxySocketAddress = httpProxy();
        if (httpProxySocketAddress != null) {
            return proxyConfiguration(Type.HTTP, httpProxySocketAddress);
        }

        InetSocketAddress httpsProxySocketAddress = httpsProxy();
        if (httpsProxySocketAddress != null) {
            return proxyConfiguration(Type.HTTPS, httpsProxySocketAddress);
        }

        InetSocketAddress socksProxySocketAddress = socksProxy();
        if (socksProxySocketAddress != null) {
            return proxyConfiguration(Type.SOCKS5, socksProxySocketAddress);
        }

        return null;
    }

    public static ProxyConfiguration proxyConfiguration(Type type, String address) {
        String[] addressParts = address.split(":");
        if (addressParts.length != 2) {
            throw new IllegalArgumentException("Proxy address must be in the format <host>:<ip>, for example 127.0.0.1:9090 or localhost:9090");
        } else {
            try {
                return proxyConfiguration(type, new InetSocketAddress(addressParts[0], Integer.parseInt(addressParts[1])));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Proxy address port \"" + addressParts[1] + "\" into an integer");
            }
        }
    }

    public static ProxyConfiguration proxyConfiguration(Type type, InetSocketAddress address) {
        return new ProxyConfiguration(type, address);
    }

    public Type getType() {
        return type;
    }

    public InetSocketAddress getProxyAddress() {
        return proxyAddress;
    }

    public enum Type {
        HTTP,
        HTTPS,
        SOCKS5
    }
}
