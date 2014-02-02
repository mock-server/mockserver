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
package org.mockserver.netty.proxy.direct;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.netty.logging.LoggingHandler;
import org.mockserver.netty.proxy.http.direct.DirectProxyUpstreamHandler;
import org.mockserver.netty.proxy.interceptor.RequestInterceptor;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class DirectProxy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();

    public DirectProxy(final int localPort, final String remoteHost, final int remotePort, final boolean secure) throws Exception {
        final SettableFuture<String> hasConnected = SettableFuture.create();
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.err.println("Proxying *:" + localPort + " to " + remoteHost + ':' + remotePort + " ...");

                try {
                    ChannelFuture directChannel =
                            new ServerBootstrap()
                                    .group(bossGroup, workerGroup)
                                    .channel(NioServerSocketChannel.class)
                                    .childHandler(new ChannelInitializer<SocketChannel>() {
                                        @Override
                                        public void initChannel(SocketChannel ch) throws Exception {
                                            // Create a default pipeline implementation.
                                            ChannelPipeline pipeline = ch.pipeline();

                                            // add HTTPS support
                                            if (secure) {
                                                SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                                engine.setUseClientMode(false);
                                                pipeline.addLast("ssl", new SslHandler(engine));
                                            }

                                            if (logger.isDebugEnabled()) {
                                                // add logging
                                                pipeline.addLast("logger", new LoggingHandler());
                                            }

                                            // add handler
                                            pipeline.addLast(new DirectProxyUpstreamHandler(new InetSocketAddress(remoteHost, remotePort), secure, 1048576, new RequestInterceptor(), "<--- "));
                                        }
                                    })
                                    .childOption(ChannelOption.AUTO_READ, false)
                                    .bind(localPort).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasConnected.set("CONNECTED");
                                    } else {
                                        hasConnected.setException(future.cause());
                                    }
                                }
                            });
                    directChannel.channel().closeFuture().sync();
                } catch (Exception e) {
                    throw new RuntimeException("Exception running direct proxy", e);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();
        hasConnected.get();
    }

    public static void main(String[] args) throws Exception {
        new DirectProxy(9090, "www.london-squash-league.com", 443, true);
    }

    public void stop() {
        if (!bossGroup.isShutdown()) {
            bossGroup.shutdownGracefully(1, 5, TimeUnit.SECONDS);
        }
        if (!workerGroup.isShutdown()) {
            workerGroup.shutdownGracefully(1, 5, TimeUnit.SECONDS);
        }
    }
}
