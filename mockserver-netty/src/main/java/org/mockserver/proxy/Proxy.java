package org.mockserver.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see ProxyBuilder
 */
public class Proxy<T extends LifeCycle> extends LifeCycle<T> {

    public static final AttributeKey<Proxy> HTTP_PROXY = AttributeKey.valueOf("HTTP_PROXY");
    public static final AttributeKey<InetSocketAddress> HTTP_CONNECT_SOCKET = AttributeKey.valueOf("HTTP_CONNECT_SOCKET");
    public static final AttributeKey<Set> LOCAL_HOST_HEADERS = AttributeKey.valueOf("LOCAL_HOST_HEADERS");
    public static final AttributeKey<Boolean> PROXYING = AttributeKey.valueOf("PROXYING");

    public static boolean isProxyingRequest(ChannelHandlerContext ctx) {
        if (ctx != null && ctx.channel().attr(PROXYING).get() != null) {
            return ctx.channel().attr(PROXYING).get();
        }
        return false;
    }

    public static Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
        if (ctx != null &&
            ctx.channel().attr(LOCAL_HOST_HEADERS) != null &&
            ctx.channel().attr(LOCAL_HOST_HEADERS).get() != null) {
            return ctx.channel().attr(LOCAL_HOST_HEADERS).get();
        }
        return new HashSet<>();
    }

}
