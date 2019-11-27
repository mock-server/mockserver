package org.mockserver.echo.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.stop.Stoppable;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class EchoServer implements Stoppable {

    static final AttributeKey<MockServerEventLog> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    static final AttributeKey<NextResponse> NEXT_RESPONSE = AttributeKey.valueOf("NEXT_RESPONSE");
    private static final MockServerLogger mockServerLogger = new MockServerLogger(EchoServer.class);

    private final Scheduler scheduler = new Scheduler(mockServerLogger);
    private final MockServerEventLog logFilter = new MockServerEventLog(mockServerLogger, scheduler, true);
    private final NextResponse nextResponse = new NextResponse();
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final CompletableFuture<Integer> boundPort = new CompletableFuture<>();
    private final List<String> registeredClients;
    private final List<Channel> websocketChannels;
    private final List<TextWebSocketFrame> textWebSocketFrames;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public EchoServer(final boolean secure) {
        this(secure, null);
    }

    public EchoServer(final boolean secure, final Error error) {
        registeredClients = new ArrayList<>();
        websocketChannels = new ArrayList<>();
        textWebSocketFrames = new ArrayList<>();
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            new ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(EchoServer.class))
                .childHandler(new EchoServerInitializer(mockServerLogger, secure, error, registeredClients, websocketChannels, textWebSocketFrames))
                .childAttr(LOG_FILTER, logFilter)
                .childAttr(NEXT_RESPONSE, nextResponse)
                .bind(0)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        boundPort.complete(((InetSocketAddress) future.channel().localAddress()).getPort());
                    } else {
                        boundPort.completeExceptionally(future.cause());
                        eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                    }
                });
        }, "MockServer EchoServer Thread").start();

        try {
            // wait for proxy to start all channels
            boundPort.get();
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while waiting for proxy to complete starting up")
                    .setThrowable(e)
            );
        }
    }

    public void stop() {
        scheduler.shutdown();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        stop();
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

    public void withNextResponse(HttpResponse... httpResponses) {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        nextResponse.httpResponse.addAll(Arrays.asList(httpResponses));
    }

    public void clearNextResponse() {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        nextResponse.httpResponse.clear();
    }

    public List<String> getRegisteredClients() {
        return registeredClients;
    }

    public List<Channel> getWebsocketChannels() {
        return websocketChannels;
    }

    public List<TextWebSocketFrame> getTextWebSocketFrames() {
        return textWebSocketFrames;
    }

    public enum Error {
        CLOSE_CONNECTION,
        LARGER_CONTENT_LENGTH,
        SMALLER_CONTENT_LENGTH,
        RANDOM_BYTES_RESPONSE
    }

    public static class NextResponse {
        public final Queue<HttpResponse> httpResponse = new LinkedList<HttpResponse>();
    }
}
