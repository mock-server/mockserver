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
package org.mockserver.helloworld;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.netty.logging.LoggingHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class HttpHelloWorldServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();

    public HttpHelloWorldServer(final int port, final int securePort) throws Exception {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    logger.warn("STARTING SERVER FOR HTTP ON PORT: " + port);
                    new ServerBootstrap()
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ChannelPipeline pipeline = ch.pipeline();

                                    pipeline.addLast("logger", new LoggingHandler("TEST_SERVER"));
                                    pipeline.addLast("codec", new HttpServerCodec());
                                    pipeline.addLast("handler", new HttpHelloWorldServerHandler());
                                }
                            })
                            .bind(port)
                            .sync().channel()
                            .closeFuture().sync();
                } catch (Exception e) {
                    throw new RuntimeException("Exception running test server", e);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    logger.warn("STARTING SERVER FOR HTTPS ON PORT: " + securePort);
                    new ServerBootstrap()
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ChannelPipeline pipeline = ch.pipeline();

                                    SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                    engine.setUseClientMode(false);
                                    pipeline.addLast("ssl", new SslHandler(engine));
                                    pipeline.addLast("logger", new LoggingHandler("TEST_SERVER_SSL"));
                                    pipeline.addLast("codec", new HttpServerCodec());
                                    pipeline.addLast("handler", new HttpHelloWorldServerHandler());
                                }
                            })
                            .bind(securePort)
                            .sync().channel()
                            .closeFuture().sync();
                } catch (Exception e) {
                    throw new RuntimeException("Exception running test server", e);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        // todo add promises to fix this crappy timeout
        TimeUnit.SECONDS.sleep(2);
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
