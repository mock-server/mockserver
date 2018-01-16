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
import org.mockserver.filters.MockServerEventLog;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;


public class EchoServer {

    static final AttributeKey<MockServerEventLog> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    static final AttributeKey<NextResponse> NEXT_RESPONSE = AttributeKey.valueOf("NEXT_RESPONSE");
    static final AttributeKey<OnlyResponse> ONLY_RESPONSE = AttributeKey.valueOf("ONLY_RESPONSE");

    private final MockServerLogger mockServerLogger = new MockServerLogger(EchoServer.class);
    private final MockServerEventLog logFilter = new MockServerEventLog(mockServerLogger);
    private final NextResponse nextResponse = new NextResponse();
    private final OnlyResponse onlyResponse = new OnlyResponse();
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final SettableFuture<Integer> boundPort = SettableFuture.create();


    public EchoServer(final boolean secure) {
        this(secure, null);
    }

    public EchoServer(final boolean secure, final Error error) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                EventLoopGroup bossGroup = new NioEventLoopGroup(1);
                EventLoopGroup workerGroup = new NioEventLoopGroup();
                new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler("EchoServer Handler"))
                    .childHandler(new EchoServerInitializer(mockServerLogger, secure, error))
                    .childAttr(LOG_FILTER, logFilter)
                    .childAttr(NEXT_RESPONSE, nextResponse)
                    .childAttr(ONLY_RESPONSE, onlyResponse)
                    .bind(0)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                boundPort.set(((InetSocketAddress) future.channel().localAddress()).getPort());
                            } else {
                                boundPort.setException(future.cause());
                                eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                            }
                        }
                    });
            }
        }, "MockServer EchoServer Thread").start();

        try {
            // wait for proxy to start all channels
            boundPort.get();
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (Exception e) {
            mockServerLogger.error("Exception while waiting for proxy to complete starting up", e);
        }
    }

    public void stop() {
        eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
    }

    public Integer getPort() {
        try {
            return boundPort.get();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public MockServerEventLog requestLogFilter() {
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
