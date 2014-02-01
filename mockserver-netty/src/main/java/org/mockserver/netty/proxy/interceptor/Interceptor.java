package org.mockserver.netty.proxy.interceptor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;

/**
 * @author jamesdbloom
 */
public interface Interceptor {
    ByteBuf intercept(ChannelHandlerContext ctx, ByteBuf channelBuffer, Logger logger) throws Exception;
}
