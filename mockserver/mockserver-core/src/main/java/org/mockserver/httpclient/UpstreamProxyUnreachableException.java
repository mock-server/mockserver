package org.mockserver.httpclient;

import java.net.InetSocketAddress;

/**
 * A forward that failed because its upstream proxy ({@code forwardHttpProxy}, {@code forwardHttpsProxy} or
 * {@code forwardSocksProxy}) could not be reached: the proxy's name did not resolve, or it refused or did not answer
 * the connection. The forward's target was never contacted, so the failure says nothing about the target's health.
 * <p>
 * Its message and string form are its cause's, so a client is answered, and the failure is logged, exactly as the
 * cause alone would be; only {@link #in} tells it apart, for the forward circuit breaker and retry policy.
 */
public class UpstreamProxyUnreachableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient InetSocketAddress proxyAddress;
    private final String property;

    UpstreamProxyUnreachableException(InetSocketAddress proxyAddress, String property, Throwable cause) {
        super(cause.getMessage(), cause, true, false);
        this.proxyAddress = proxyAddress;
        this.property = property;
    }

    /**
     * @return the address of the upstream proxy that could not be reached
     */
    public InetSocketAddress getProxyAddress() {
        return proxyAddress;
    }

    /**
     * @return the configuration property that set the proxy: forwardHttpProxy, forwardHttpsProxy or forwardSocksProxy
     */
    public String getProperty() {
        return property;
    }

    @Override
    public String toString() {
        return getCause().toString();
    }

    /**
     * @return the unreachable upstream proxy {@code failure} is, or was caused by, or null
     */
    public static UpstreamProxyUnreachableException in(Throwable failure) {
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof UpstreamProxyUnreachableException) {
                return (UpstreamProxyUnreachableException) failure;
            }
        }
        return null;
    }
}
