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
package org.mockserver.netty.proxy.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.mock.MockServer;
import org.mockserver.proxy.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class HttpProxy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // netty
    private final Integer port;
    private final Integer securePort;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    // mockserver
    private final LogFilter logFilter = new LogFilter();

    public HttpProxy(Integer port, Integer securePort) {
        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");
        this.port = port;
        this.securePort = securePort;
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 1090;
        }
        new HttpProxy(port, port + 1).run();
    }

    public HttpProxy run() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Channel httpChannel = null;
                    if (port != null) {
                        httpChannel = new ServerBootstrap()
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new HttpProxyInitializer(logFilter, HttpProxy.this, securePort, false))
                                .option(ChannelOption.SO_BACKLOG, 128)
                                .childOption(ChannelOption.SO_KEEPALIVE, true)
                                .bind(port)
                                .sync()
                                .channel();
                    }
                    Channel httpsChannel = null;
                    if (securePort != null) {
                        httpsChannel = new ServerBootstrap()
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new HttpProxyInitializer(logFilter, HttpProxy.this, securePort, true))
                                .option(ChannelOption.SO_BACKLOG, 128)
                                .childOption(ChannelOption.SO_KEEPALIVE, true)
                                .bind(securePort)
                                .sync()
                                .channel();
                    }
                    if (httpChannel != null) {
                        httpChannel.closeFuture().sync();
                    }
                    if (httpsChannel != null) {
                        httpsChannel.closeFuture().sync();
                    }
                } catch (InterruptedException ie) {
                    logger.error("MockServer Netty Channel receive InterruptedException", ie);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();
        return this;
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
