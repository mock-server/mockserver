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
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.stop.Stoppable;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.configuration.Configuration.configuration;


public class EchoServer implements Stoppable {

    static final AttributeKey<MockServerEventLog> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    static final AttributeKey<NextResponse> NEXT_RESPONSE = AttributeKey.valueOf("NEXT_RESPONSE");
    static final AttributeKey<LastRequest> LAST_REQUEST = AttributeKey.valueOf("LAST_REQUEST");
    private static final MockServerLogger mockServerLogger = new MockServerLogger(EchoServer.class);

    private final Configuration configuration = configuration();
    private final Scheduler scheduler = new Scheduler(configuration, mockServerLogger);
    private final MockServerEventLog mockServerEventLog = new MockServerEventLog(configuration, mockServerLogger, scheduler, true);
    private final NextResponse nextResponse = new NextResponse();
    private final LastRequest lastRequest = new LastRequest();
    private final CompletableFuture<Integer> boundPort = new CompletableFuture<>();
    private final List<String> registeredClients;
    private final List<Channel> websocketChannels;
    private final List<TextWebSocketFrame> textWebSocketFrames;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public EchoServer(final boolean secure) {
        this(secure, null, null);
    }

    public EchoServer(final SslContext sslContext) {
        this(true, sslContext, null);
    }

    public EchoServer(final boolean secure, final Error error) {
        this(secure, null, error);
    }

    public EchoServer(final boolean secure, final SslContext sslContext, final Error error) {
        registeredClients = new ArrayList<>();
        websocketChannels = new ArrayList<>();
        textWebSocketFrames = new ArrayList<>();
        new Scheduler.SchedulerThreadFactory("MockServer EchoServer Thread").newThread(() -> {
            bossGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-bossEventLoop"));
            workerGroup = new NioEventLoopGroup(5, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-workerEventLoop"));
            new ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(EchoServer.class))
                .childHandler(new EchoServerInitializer(configuration, mockServerLogger, secure, sslContext, error, registeredClients, websocketChannels, textWebSocketFrames))
                .childAttr(LOG_FILTER, mockServerEventLog)
                .childAttr(NEXT_RESPONSE, nextResponse)
                .childAttr(LAST_REQUEST, lastRequest)
                .bind(0)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        boundPort.complete(((InetSocketAddress) future.channel().localAddress()).getPort());
                    } else {
                        boundPort.completeExceptionally(future.cause());
                    }
                });
        }).start();

        try {
            // wait for proxy to start all channels
            boundPort.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while waiting for proxy to complete starting up")
                    .setThrowable(e)
            );
        }
    }

    public void stop() {
        scheduler.shutdown();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public void close() {
        stop();
    }

    public Integer getPort() {
        try {
            return boundPort.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public MockServerEventLog mockServerEventLog() {
        return mockServerEventLog;
    }

    public void withNextResponse(HttpResponse... httpResponses) {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        nextResponse.httpResponse.addAll(Arrays.asList(httpResponses));
    }

    public void clear() {
        // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
        nextResponse.httpResponse.clear();
        lastRequest.httpRequest.set(new CompletableFuture<>());
    }

    public HttpRequest getLastRequest() {
        try {
            HttpRequest httpRequest = lastRequest.httpRequest.get().get();
            lastRequest.httpRequest.set(new CompletableFuture<>());
            return httpRequest;
        } catch (InterruptedException | ExecutionException e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while waiting to receive request - " + e.getMessage())
                    .setThrowable(e)
            );
            return null;
        }
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
        public final Queue<HttpResponse> httpResponse = new LinkedList<>();
    }

    public static class LastRequest {
        public final AtomicReference<CompletableFuture<org.mockserver.model.HttpRequest>> httpRequest = new AtomicReference<>(new CompletableFuture<>());
    }
}
