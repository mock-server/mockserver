package org.mockserver.proxyconfiguration;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.Protocol;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class ProxyConfiguration extends ObjectWithJsonToString {

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

    public static List<ProxyConfiguration> proxyConfiguration(Configuration configuration) {
        List<ProxyConfiguration> proxyConfigurations = new ArrayList<>();
        String username = configuration.forwardProxyAuthenticationUsername();
        String password = configuration.forwardProxyAuthenticationPassword();

        InetSocketAddress httpProxySocketAddress = configuration.forwardHttpProxy();
        if (httpProxySocketAddress != null) {
            proxyConfigurations.add(proxyConfiguration(Type.HTTP, httpProxySocketAddress, username, password));
        }

        InetSocketAddress httpsProxySocketAddress = configuration.forwardHttpsProxy();
        if (httpsProxySocketAddress != null) {
            proxyConfigurations.add(proxyConfiguration(Type.HTTPS, httpsProxySocketAddress, username, password));
        }

        InetSocketAddress socksProxySocketAddress = configuration.forwardSocksProxy();
        if (socksProxySocketAddress != null) {
            if (proxyConfigurations.isEmpty()) {
                proxyConfigurations.add(proxyConfiguration(Type.SOCKS5, socksProxySocketAddress, username, password));
            } else {
                throw new IllegalArgumentException("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy");
            }
        }

        return proxyConfigurations;
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

    @SuppressWarnings("UnusedReturnValue")
    public ProxyConfiguration addProxyAuthenticationHeader(HttpRequest httpRequest) {
        if (isNotBlank(username) && isNotBlank(password)) {
            httpRequest.withHeader(
                PROXY_AUTHORIZATION.toString(),
                "Basic " + Base64.encode(Unpooled.copiedBuffer(username + ':' + password, StandardCharsets.UTF_8), false).toString(StandardCharsets.US_ASCII)
            );
        }
        return this;
    }

    public enum Type {
        HTTP,
        HTTPS,
        SOCKS5
    }
}
