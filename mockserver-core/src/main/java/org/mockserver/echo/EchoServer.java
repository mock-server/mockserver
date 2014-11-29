package org.mockserver.echo;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class EchoServer {

    private NioEventLoopGroup eventLoopGroup;

    public EchoServer(final int port, final boolean secure) {
        final Logger logger = LoggerFactory.getLogger(EchoServer.class);
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
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            public void initChannel(SocketChannel channel) throws Exception {
                                ChannelPipeline pipeline = channel.pipeline();

                                if (secure) {
                                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                                    pipeline.addLast(SslContext.newServerContext(ssc.certificate(), ssc.privateKey()).newHandler(channel.alloc()));
                                }

                                pipeline.addLast(new HttpServerCodec());

                                pipeline.addLast(new HttpContentDecompressor());

                                pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                                }

                                pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {

                                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
                                        // echo back request headers and body
                                        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(request.content()));
                                        response.headers().add(request.headers());

                                        // set hop-by-hop headers
                                        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
                                        if (isKeepAlive(request)) {
                                            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                                        }
                                        if (is100ContinueExpected(request)) {
                                            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                                        }

                                        // write and flush
                                        ctx.writeAndFlush(response);
                                    }

                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        cause.printStackTrace();
                                        ctx.close();
                                    }
                                });
                            }
                        })
                        .bind(port)
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    hasStarted.set("STARTED");
                                } else {
                                    hasStarted.setException(future.cause());
                                    eventLoopGroup.shutdownGracefully();
                                }
                            }
                        });
            }
        }).start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
            TimeUnit.MICROSECONDS.sleep(100);
        } catch (Exception e) {
            logger.error("Exception while waiting for proxy to complete starting up", e);
        }
    }

    public void stop() {
        eventLoopGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
    }
}
