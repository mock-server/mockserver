package org.mockserver.echo.unification;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public class PortUnificationEchoServer {

    private NioEventLoopGroup eventLoopGroup;

    public PortUnificationEchoServer(final int port) {
        Logger logger = LoggerFactory.getLogger(PortUnificationEchoServer.class);
        final SettableFuture<String> hasStarted = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                eventLoopGroup = new NioEventLoopGroup();
                EventLoopGroup bossGroup = new NioEventLoopGroup(1);
                EventLoopGroup workerGroup = new NioEventLoopGroup();
                new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler("EchoServer Handler"))
                    .childHandler(new EchoServerPortUnificationHandler())
                    .bind(port)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                hasStarted.set("STARTED");
                            } else {
                                hasStarted.setException(future.cause());
                                eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                            }
                        }
                    });
            }
        }, "MockServer Port Unification EchoServer Thread").start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (Exception e) {
            logger.error("Exception while waiting for proxy to complete starting up", e);
        }
    }

    public void stop() {
        eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
    }
}
