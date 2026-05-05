package org.mockserver.netty.responsewriter;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
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

        ChannelFuture channelFuture = ctx.writeAndFlush(response);
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
                                    if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                                        mockServerLogger
                                            .logEvent(new LogEntry()
                                                .setLogLevel(TRACE)
                                                .setMessageFormat("disconnected and closed socket " + future.channel().localAddress())
                                            );
                                    }
                                } else {
                                    if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
                                        mockServerLogger
                                            .logEvent(new LogEntry()
                                                .setLogLevel(WARN)
                                                .setMessageFormat("exception closing socket " + future.channel().localAddress())
                                                .setThrowable(disconnectFuture.cause())
                                            );
                                    }
                                }
                            });
                    } else if (MockServerLogger.isEnabled(WARN) && mockServerLogger != null) {
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
