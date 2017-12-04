package org.mockserver.proxy;

import io.netty.util.AttributeKey;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.mock.HttpStateHandler;

import java.net.InetSocketAddress;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see ProxyBuilder
 */
public class Proxy<T extends LifeCycle> extends LifeCycle<T> {

    public static final AttributeKey<Proxy> HTTP_PROXY = AttributeKey.valueOf("HTTP_PROXY");
    public static final AttributeKey<HttpStateHandler> STATE_HANDLER = AttributeKey.valueOf("PROXY_STATE_HANDLER");
    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    public static final AttributeKey<InetSocketAddress> HTTP_CONNECT_SOCKET = AttributeKey.valueOf("HTTP_CONNECT_SOCKET");

}
