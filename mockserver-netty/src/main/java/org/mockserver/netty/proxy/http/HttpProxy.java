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

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.netty.logging.LoggingHandler;
import org.mockserver.netty.proxy.http.direct.DirectProxyUpstreamHandler;
import org.mockserver.netty.proxy.interceptor.RequestInterceptor;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class HttpProxy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    // mockserver
    private final LogFilter logFilter = new LogFilter();

    public HttpProxy(final Integer port,
                     final Integer securePort,
                     final Integer socksPort,
                     final Integer directLocalPort,
                     final Integer directLocalSecurePort,
                     final String directRemoteHost,
                     final Integer directRemotePort) throws InterruptedException, ExecutionException {
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP proxy & HTTPS CONNECT port [" + port + "]");
            logger.debug("HTTPS proxy port [" + securePort + "]");
            logger.debug("SOCKS proxy port [" + socksPort + "]");
            logger.debug("Direct proxy from port [" + directLocalPort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
            logger.debug("Direct SSL proxy from port [" + directLocalSecurePort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
        }

        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");

        final SettableFuture<String> hasConnected = SettableFuture.create();
        final SettableFuture<String> hasSecureConnected = SettableFuture.create();
        final SettableFuture<String> hasSOCKSConnected = SettableFuture.create();
        final SettableFuture<String> hasDirectConnected = SettableFuture.create();
        final SettableFuture<String> hasDirectSecureConnected = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelFuture httpChannel = null;
                    if (port != null) {

                        httpChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add HTTP decoder and encoder
                                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("HTTP"));
                                }

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, securePort != null ? new InetSocketAddress(securePort) : null, false));
                            }
                        }, port, true, hasConnected);
                    } else {
                        hasConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture httpsChannel = null;
                    if (securePort != null) {

                        httpsChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add HTTPS support
                                SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                engine.setUseClientMode(false);
                                pipeline.addLast(SslHandler.class.getSimpleName(), new SslHandler(engine));

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("HTTPS"));
                                }

                                // add HTTP decoder and encoder
                                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, new InetSocketAddress(securePort), true));
                            }
                        }, securePort, true, hasSecureConnected);
                    } else {
                        hasSecureConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture socksChannel = null;
                    if (socksPort != null && port != null) {
                        socksChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add SOCKS decoder and encoder
                                pipeline.addLast(SocksInitRequestDecoder.class.getSimpleName(), new SocksInitRequestDecoder());
                                pipeline.addLast(SocksMessageEncoder.class.getSimpleName(), new SocksMessageEncoder());

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("SOCKS"));
                                }

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, new InetSocketAddress(port), false));
                            }
                        }, socksPort, true, hasSOCKSConnected);
                    } else {
                        hasSOCKSConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture directChannel = null;
                    if (directLocalPort != null && directRemoteHost != null && directRemotePort != null) {
                        directChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                ChannelPipeline pipeline = ch.pipeline();

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("DIRECT HTTP"));
                                }

                                // add handler
                                pipeline.addLast(new DirectProxyUpstreamHandler(new InetSocketAddress(directRemoteHost, directRemotePort), false, 1048576, new RequestInterceptor(), "<--- "));
                            }
                        }, directLocalPort, false, hasDirectConnected);
                    } else {
                        hasDirectConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture directSecureChannel = null;
                    if (directLocalSecurePort != null && directRemoteHost != null && directRemotePort != null) {

                        directSecureChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                ChannelPipeline pipeline = ch.pipeline();

                                // add HTTPS support
                                SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                engine.setUseClientMode(false);
                                pipeline.addLast("ssl", new SslHandler(engine));

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("DIRECT HTTPS"));
                                }

                                // add handler
                                pipeline.addLast(new DirectProxyUpstreamHandler(new InetSocketAddress(directRemoteHost, directRemotePort), true, 1048576, new RequestInterceptor(), "<--- "));
                            }
                        }, directLocalSecurePort, false, hasDirectSecureConnected);
                    } else {
                        hasDirectSecureConnected.set("NOT CONNECTED");
                    }
                    if (httpChannel != null) {
                        httpChannel.channel().closeFuture().sync();
                    }
                    if (httpsChannel != null) {
                        httpsChannel.channel().closeFuture().sync();
                    }
                    if (socksChannel != null) {
                        socksChannel.channel().closeFuture().sync();
                    }
                    if (directChannel != null) {
                        directChannel.channel().closeFuture().sync();
                    }
                    if (directSecureChannel != null) {
                        directSecureChannel.channel().closeFuture().sync();
                    }
                } catch (InterruptedException ie) {
                    logger.error("Proxy receive InterruptedException", ie);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        // wait for connection
        hasConnected.get();
        hasSecureConnected.get();
        hasSOCKSConnected.get();
        hasDirectConnected.get();
        hasDirectSecureConnected.get();
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 1090;
        }
        new HttpProxy(port, port + 1, port + 2, port + 3, port + 4, "www.mock-server.com", 80);
    }

    private ChannelFuture createBootstrap(final ChannelInitializer<SocketChannel> childHandler, final Integer port, boolean autoRead, final SettableFuture<String> hasConnected) throws InterruptedException {
        return new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(childHandler)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.AUTO_READ, autoRead)
                .bind(port).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            hasConnected.set("CONNECTED");
                        } else {
                            hasConnected.setException(future.cause());
                        }
                    }
                });
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
