package org.mockserver.netty.responsewriter;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpObject;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class NettyResponseWriter extends ResponseWriter {

    private final ChannelHandlerContext ctx;
    private final Scheduler scheduler;

    public NettyResponseWriter(Configuration configuration, MockServerLogger mockServerLogger, ChannelHandlerContext ctx, Scheduler scheduler) {
        super(configuration, mockServerLogger);
        this.ctx = ctx;
        this.scheduler = scheduler;
    }

    @Override
    public void sendResponse(HttpRequest request, HttpResponse response) {
        writeAndCloseSocket(ctx, request, response);
    }

    private void writeAndCloseSocket(final ChannelHandlerContext ctx, final HttpRequest request, HttpResponse response) {
        boolean closeChannel;

        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getCloseSocket() != null) {
            closeChannel = connectionOptions.getCloseSocket();
        } else {
            closeChannel = !(request.isKeepAlive() != null && request.isKeepAlive());
        }

        Delay chunkDelay = connectionOptions != null ? connectionOptions.getChunkDelay() : null;
        Integer chunkSize = connectionOptions != null ? connectionOptions.getChunkSize() : null;
        if (chunkDelay != null && chunkSize != null && chunkSize > 0) {
            writeChunkedResponseWithDelay(ctx, response, connectionOptions, closeChannel, chunkDelay);
        } else {
            ChannelFuture channelFuture = ctx.writeAndFlush(response);
            addCloseSocketListener(channelFuture, connectionOptions, closeChannel);
        }
    }

    private void writeChunkedResponseWithDelay(
        final ChannelHandlerContext ctx,
        HttpResponse response,
        ConnectionOptions connectionOptions,
        boolean closeChannel,
        Delay chunkDelay
    ) {
        List<DefaultHttpObject> httpObjects = new org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse(mockServerLogger)
            .mapMockServerResponseToNettyResponse(response);
        if (httpObjects.size() <= 1) {
            ChannelFuture channelFuture = ctx.writeAndFlush(response);
            addCloseSocketListener(channelFuture, connectionOptions, closeChannel);
            return;
        }
        ChannelFuture headerFuture = ctx.writeAndFlush(httpObjects.get(0));
        headerFuture.addListener(f -> {
            if (!f.isSuccess()) {
                for (int i = 1; i < httpObjects.size(); i++) {
                    ReferenceCountUtil.release(httpObjects.get(i));
                }
                addCloseSocketListener(headerFuture, connectionOptions, closeChannel);
                return;
            }
            long cumulativeDelayMs = 0;
            for (int i = 1; i < httpObjects.size(); i++) {
                final DefaultHttpObject chunk = httpObjects.get(i);
                final boolean isLast = (i == httpObjects.size() - 1);
                cumulativeDelayMs += chunkDelay.sampleValueMillis();
                ctx.executor().schedule(() -> {
                    if (ctx.channel().isActive()) {
                        ChannelFuture chunkFuture = ctx.writeAndFlush(chunk);
                        if (isLast) {
                            addCloseSocketListener(chunkFuture, connectionOptions, closeChannel);
                        }
                    } else {
                        ReferenceCountUtil.release(chunk);
                    }
                }, cumulativeDelayMs, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void addCloseSocketListener(ChannelFuture channelFuture, ConnectionOptions connectionOptions, boolean closeChannel) {
        if (closeChannel || configuration.alwaysCloseSocketConnections()) {
            channelFuture.addListener((ChannelFutureListener) future -> {
                Delay closeSocketDelay = connectionOptions != null ? connectionOptions.getCloseSocketDelay() : null;
                if (closeSocketDelay == null) {
                    disconnectAndCloseChannel(future);
                } else {
                    scheduler.schedule(() -> disconnectAndCloseChannel(future), false, closeSocketDelay);
                }
            });
        }
    }

    private void disconnectAndCloseChannel(ChannelFuture future) {
        future
            .channel()
            .disconnect()
            .addListener(disconnectFuture -> {
                    if (disconnectFuture.isSuccess()) {
                        future
                            .channel()
                            .close()
                            .addListener(closeFuture -> {
                                if (disconnectFuture.isSuccess()) {
                                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(TRACE)) {
                                        mockServerLogger
                                            .logEvent(new LogEntry()
                                                .setLogLevel(TRACE)
                                                .setMessageFormat("disconnected and closed socket " + future.channel().localAddress())
                                            );
                                    }
                                } else {
                                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                                        mockServerLogger
                                            .logEvent(new LogEntry()
                                                .setLogLevel(WARN)
                                                .setMessageFormat("exception closing socket " + future.channel().localAddress())
                                                .setThrowable(disconnectFuture.cause())
                                            );
                                    }
                                }
                            });
                    } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger
                            .logEvent(new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("exception disconnecting socket " + future.channel().localAddress())
                                .setThrowable(disconnectFuture.cause()));
                    }
                }
            );
    }

}
