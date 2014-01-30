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

import com.google.common.base.Charsets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.mockserver.netty.proxy.LoggingHandler;
import org.mockserver.socket.SSLFactory;

import javax.net.ssl.SSLEngine;

public class HexDumpProxyFrontendHandler extends ChannelDuplexHandler {

    private final String remoteHost;
    private final int remotePort;
    private volatile Channel outboundChannel;

    public HexDumpProxyFrontendHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new HexDumpProxyBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        ChannelPipeline pipeline = ctx.pipeline();

        // add SSL support
        SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
        engine.setUseClientMode(false);
        pipeline.addLast("ssl", new SslHandler(engine));

        // logging
        pipeline.addLast("backend-logging", new LoggingHandler("                                    -------->"));

        ChannelFuture channelFuture = bootstrap.connect(remoteHost, remotePort);
        outboundChannel = channelFuture.channel();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    inboundChannel.writeAndFlush(Unpooled.copiedBuffer("HTTP/1.1 200 OK", Charsets.UTF_8)).addListeners(
                            new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    // connection complete start to read first data
                                    inboundChannel.read();
                                }
                            }
                    );
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
        super.close(ctx, future);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (outboundChannel == null) {
            channelActive(ctx);
        }
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // was able to flush out data, start to read the next chunk
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        Channel ch = ctx.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
