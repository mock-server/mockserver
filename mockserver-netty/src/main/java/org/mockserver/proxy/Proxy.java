package org.mockserver.proxy;

import io.netty.util.AttributeKey;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.stop.Stoppable;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @see ProxyBuilder
 *
 * @author jamesdbloom
 */
public interface Proxy extends Stoppable {

    AttributeKey<Proxy> HTTP_PROXY = AttributeKey.valueOf("HTTP_PROXY");
    AttributeKey<RequestLogFilter> REQUEST_LOG_FILTER = AttributeKey.valueOf("PROXY_REQUEST_LOG_FILTER");
    AttributeKey<RequestResponseLogFilter> REQUEST_RESPONSE_LOG_FILTER = AttributeKey.valueOf("PROXY_REQUEST_RESPONSE_LOG_FILTER");
    AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    AttributeKey<InetSocketAddress> HTTP_CONNECT_SOCKET = AttributeKey.valueOf("HTTP_CONNECT_SOCKET");

    boolean isRunning();

    List<Integer> getPorts();

    List<Integer> bindToPorts(final List<Integer> requestedPortBindings);

}
