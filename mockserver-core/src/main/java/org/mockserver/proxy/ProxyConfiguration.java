package org.mockserver.proxy;

import java.net.InetSocketAddress;

import static org.mockserver.configuration.ConfigurationProperties.*;

/**
 * @author jamesdbloom
 */
public class ProxyConfiguration {

    private final Type type;
    private final InetSocketAddress proxyAddress;
    private final String username;
    private final String password;

    private ProxyConfiguration(Type type, InetSocketAddress proxyAddress, String username, String password) {
        this.type = type;
        this.proxyAddress = proxyAddress;
        this.username = username;
        this.password = password;
    }

    @SuppressWarnings("deprecation")
    public static ProxyConfiguration proxyConfiguration() {
        String username = forwardProxyAuthenticationUsername();
        String password = forwardProxyAuthenticationPassword();

        InetSocketAddress httpProxySocketAddress = forwardHttpProxy();
        if (httpProxySocketAddress == null) {
            httpProxySocketAddress = httpProxy();
        }
        if (httpProxySocketAddress != null) {
            return proxyConfiguration(Type.HTTP, httpProxySocketAddress, username, password);
        }

        InetSocketAddress httpsProxySocketAddress = forwardHttpsProxy();
        if (httpsProxySocketAddress == null) {
            httpsProxySocketAddress = httpsProxy();
        }
        if (httpsProxySocketAddress != null) {
            return proxyConfiguration(Type.HTTPS, httpsProxySocketAddress, username, password);
        }

        InetSocketAddress socksProxySocketAddress = forwardSocksProxy();
        if (socksProxySocketAddress == null) {
            socksProxySocketAddress = socksProxy();
        }
        if (socksProxySocketAddress != null) {
            return proxyConfiguration(Type.SOCKS5, socksProxySocketAddress, username, password);
        }

        return null;
    }

    public static ProxyConfiguration proxyConfiguration(Type type, String address) {
        return proxyConfiguration(type, address, null, null);
    }

    public static ProxyConfiguration proxyConfiguration(Type type, InetSocketAddress address) {
        return proxyConfiguration(type, address, null, null);
    }

    public static ProxyConfiguration proxyConfiguration(Type type, String address, String username, String password) {
        String[] addressParts = address.split(":");
        if (addressParts.length != 2) {
            throw new IllegalArgumentException("Proxy address must be in the format <host>:<ip>, for example 127.0.0.1:9090 or localhost:9090");
        } else {
            try {
                return proxyConfiguration(type, new InetSocketAddress(addressParts[0], Integer.parseInt(addressParts[1])), username, password);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Proxy address port \"" + addressParts[1] + "\" into an integer");
            }
        }
    }

    public static ProxyConfiguration proxyConfiguration(Type type, InetSocketAddress address, String username, String password) {
        return new ProxyConfiguration(type, address, username, password);
    }

    public Type getType() {
        return type;
    }

    public InetSocketAddress getProxyAddress() {
        return proxyAddress;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public enum Type {
        HTTP,
        HTTPS,
        SOCKS5
    }
}
