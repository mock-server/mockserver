package org.mockserver.echo.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.mockserver.filters.LogFilter;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;


public class EchoServer {

    static final AttributeKey<LogFilter> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    static final AttributeKey<NextResponse> NEXT_RESPONSE = AttributeKey.valueOf("NEXT_RESPONSE");
    static final AttributeKey<OnlyResponse> ONLY_RESPONSE = AttributeKey.valueOf("ONLY_RESPONSE");

    private final LogFilter logFilter = new LogFilter(new LoggingFormatter(LoggerFactory.getLogger(this.getClass()), null));
    private final NextResponse nextResponse = new NextResponse();
    private final OnlyResponse onlyResponse = new OnlyResponse();
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();


    public EchoServer(final int port, final boolean secure) {
        this(port, secure, null);
    }

    public EchoServer(final int port, final boolean secure, final Error error) {
        Logger logger = LoggerFactory.getLogger(EchoServer.class);
        final SettableFuture<String> hasStarted = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                EventLoopGroup bossGroup = new NioEventLoopGroup(1);
                EventLoopGroup workerGroup = new NioEventLoopGroup();
                new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler("EchoServer Handler"))
                    .childHandler(new EchoServerInitializer(secure, error))
                    .childAttr(LOG_FILTER, logFilter)
                    .childAttr(NEXT_RESPONSE, nextResponse)
                    .childAttr(ONLY_RESPONSE, onlyResponse)
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
        }, "MockServer EchoServer Thread").start();

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

    public LogFilter requestLogFilter() {
        return logFilter;
    }

    public EchoServer withNextResponse(HttpResponse... httpResponses) {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        nextResponse.httpResponse.addAll(Arrays.asList(httpResponses));
        return this;
    }

    public EchoServer withOnlyResponse(HttpResponse httpResponse) {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        onlyResponse.httpResponse = httpResponse;
        return this;
    }

    public enum Error {
        CLOSE_CONNECTION,
        LARGER_CONTENT_LENGTH,
        SMALLER_CONTENT_LENGTH,
        RANDOM_BYTES_RESPONSE
    }

    public class NextResponse {
        public final Queue<HttpResponse> httpResponse = new LinkedList<HttpResponse>();
    }

    public class OnlyResponse {
        public HttpResponse httpResponse;
    }
}
