package org.mockserver.proxy;

import io.netty.util.AttributeKey;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.lifecycle.LifeCycle;

import java.net.InetSocketAddress;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see ProxyBuilder
 */
public class Proxy<T extends LifeCycle> extends LifeCycle<T> {

    public static final AttributeKey<Proxy> HTTP_PROXY = AttributeKey.valueOf("HTTP_PROXY");
    public static final AttributeKey<RequestLogFilter> REQUEST_LOG_FILTER = AttributeKey.valueOf("PROXY_REQUEST_LOG_FILTER");
    public static final AttributeKey<RequestResponseLogFilter> REQUEST_RESPONSE_LOG_FILTER = AttributeKey.valueOf("PROXY_REQUEST_RESPONSE_LOG_FILTER");
    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    public static final AttributeKey<InetSocketAddress> HTTP_CONNECT_SOCKET = AttributeKey.valueOf("HTTP_CONNECT_SOCKET");

}
