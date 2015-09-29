package org.mockserver.proxy;

import io.netty.util.AttributeKey;
import org.mockserver.filters.LogFilter;
import org.mockserver.stop.Stoppable;

import java.net.InetSocketAddress;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @see ProxyBuilder
 *
 * @author jamesdbloom
 */
public interface Proxy extends Stoppable {

    public static final AttributeKey<Proxy> HTTP_PROXY = AttributeKey.valueOf("HTTP_PROXY");
    public static final AttributeKey<LogFilter> LOG_FILTER = AttributeKey.valueOf("PROXY_LOG_FILTER");
    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    public static final AttributeKey<InetSocketAddress> HTTP_CONNECT_SOCKET = AttributeKey.valueOf("HTTP_CONNECT_SOCKET");
    public static final AttributeKey<Boolean> ONWARD_SSL_UNKNOWN = AttributeKey.valueOf("ONWARD_SSL_UNKNOWN");

    public boolean isRunning();

}
