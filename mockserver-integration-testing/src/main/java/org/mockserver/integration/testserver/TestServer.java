package org.mockserver.integration.testserver;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class TestServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();

    public TestServer startServer(final int port, final int securePort) {
        final SettableFuture<String> hasBoundToHTTPPort = SettableFuture.create();
        final SettableFuture<String> hasBoundToHTTPSPort = SettableFuture.create();
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    logger.warn("STARTING SERVER FOR HTTP ON PORT: " + port);
                    ChannelFuture channelFutureHTTP = new ServerBootstrap()
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ChannelPipeline pipeline = ch.pipeline();

                                    pipeline.addLast("logger", new LoggingHandler("TEST_SERVER"));
                                    pipeline.addLast("codec", new HttpServerCodec());
                                    pipeline.addLast("handler", new TestServerHandler());
                                }
                            })
                            .bind(port).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasBoundToHTTPPort.set("CONNECTED");
                                    } else {
                                        hasBoundToHTTPPort.setException(future.cause());
                                    }
                                }
                            });

                    logger.warn("STARTING SERVER FOR HTTPS ON PORT: " + securePort);
                    ChannelFuture channelFutureHTTPS = new ServerBootstrap()
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ChannelPipeline pipeline = ch.pipeline();

                                    pipeline.addLast("raw logger", new LoggingHandler("RAW TEST_SERVER_SSL"));
                                    SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                    engine.setUseClientMode(false);
                                    pipeline.addLast("ssl", new SslHandler(engine));
                                    pipeline.addLast("logger", new LoggingHandler("TEST_SERVER_SSL"));
                                    pipeline.addLast("codec", new HttpServerCodec());
                                    pipeline.addLast("handler", new TestServerHandler());
                                }
                            })
                            .bind(securePort).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasBoundToHTTPSPort.set("CONNECTED");
                                    } else {
                                        hasBoundToHTTPSPort.setException(future.cause());
                                    }
                                }
                            });

                    channelFutureHTTP.channel().closeFuture().sync();
                    channelFutureHTTPS.channel().closeFuture().sync();
                } catch (Exception e) {
                    throw new RuntimeException("Exception running test server", e);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        try {
            // wait for connection
            hasBoundToHTTPPort.get();
            hasBoundToHTTPSPort.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }

        return this;
    }

    public void stop() {
        workerGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
        bossGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
    }
}
