/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.mockserver.netty.proxy.http.connect;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.netty.logging.LoggingHandler;
import org.mockserver.netty.proxy.ProxyRelayHandler;
import org.mockserver.netty.proxy.interceptor.RequestInterceptor;
import org.mockserver.netty.proxy.interceptor.ResponseInterceptor;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

@ChannelHandler.Sharable
public final class ProxyConnectHandler extends SimpleChannelInboundHandler<HttpRequest> {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Bootstrap bootstrap = new Bootstrap();
    private final int connectPort;

    public ProxyConnectHandler(int connectPort) {
        this.connectPort = connectPort;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(
                new GenericFutureListener<Future<Channel>>() {
                    @Override
                    public void operationComplete(final Future<Channel> future) throws Exception {
                        final Channel outboundChannel = future.getNow();
                        if (future.isSuccess()) {
                            ctx.channel().writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
                                    .addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                                            ctx.pipeline().remove("decoder-encoder");
                                            ctx.pipeline().remove(ProxyConnectHandler.class.getSimpleName());
                                            // downstream
                                            SSLEngine clientEngine = SSLFactory.getClientContext().createSSLEngine();
                                            clientEngine.setUseClientMode(true);
                                            outboundChannel.pipeline().addLast("outbound relay ssl", new SslHandler(clientEngine));
                                            outboundChannel.pipeline().addLast("outbound relay logger", new LoggingHandler("                -->"));
                                            outboundChannel.pipeline().addLast(new ProxyRelayHandler(ctx.channel(), 1048576, new RequestInterceptor(), "                -->"));
                                            // upstream
                                            SSLEngine serverEngine = SSLFactory.getServerContext().createSSLEngine();
                                            serverEngine.setUseClientMode(false);
                                            ctx.channel().pipeline().addLast("upstream relay ssl", new SslHandler(serverEngine));
                                            ctx.channel().pipeline().addLast("upstream relay logger", new LoggingHandler("<-- "));
                                            ctx.channel().pipeline().addLast(new ProxyRelayHandler(outboundChannel, 1048576, new ResponseInterceptor(), "<-- "));
                                        }
                                    });
                        } else {
                            failure("Failed to activate handler and retrieve channel", future, ctx);
                        }
                    }
                });

        final Channel inboundChannel = ctx.channel();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new DirectClientInitializer(promise));

        String hostName = StringUtils.substringBefore(request.headers().get(HttpHeaders.Names.HOST), ":");
        Integer port = Integer.parseInt(StringUtils.substringAfter(request.headers().get(HttpHeaders.Names.HOST), ":"));
        logger.warn("Connecting to [" + hostName + ":" + port + "]");

        bootstrap.connect("127.0.0.1", connectPort).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    failure("CONNECT FAILED TO 127.0.0.1:" + connectPort, future, ctx);
                }
            }
        });
    }

    private void failure(String message, Future future, ChannelHandlerContext ctx) {
        logger.warn(message, future.cause());
        Channel channel = ctx.channel();
        channel.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY));
        if (channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("EXCEPTION CAUGHT", cause);
        Channel ch = ctx.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
