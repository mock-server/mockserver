package org.mockserver.netty.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;

import static org.mockserver.exception.ExceptionHandling.directMemoryLimitReached;

/**
 * The last handler of the HTTP/3 UDP listener's pipeline, behind the QUIC codec, which takes no exception itself. It
 * logs each exception that reaches it in MockServer's log, where Netty would log it at {@code WARN} with a stack
 * trace through its own logger.
 * <p>
 * It closes nothing. Netty's NIO datagram channel keeps reading after a {@link SocketException}, which can then recur
 * with each datagram, so only the first of each class is a warning; it closes the listener after any other read error.
 * The epoll datagram channel keeps reading after every read error, whose class is not a {@link SocketException}, so
 * there every other {@link IOException} is warned of once per class and message, as one class carries every errno.
 * One instance serves one listener, and its events all arrive on that listener's event loop.
 */
public class Http3ListenerExceptionHandler extends ChannelInboundHandlerAdapter {

    private final MockServerLogger mockServerLogger;
    private final boolean keepsReadingAfterEveryReadError;
    private final Set<String> errorsWarnedOf = new HashSet<>();

    /**
     * @param keepsReadingAfterEveryReadError true for a listener that does not close on a read error (epoll)
     */
    public Http3ListenerExceptionHandler(MockServerLogger mockServerLogger, boolean keepsReadingAfterEveryReadError) {
        this.mockServerLogger = mockServerLogger;
        this.keepsReadingAfterEveryReadError = keepsReadingAfterEveryReadError;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (directMemoryLimitReached(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("direct memory limit (io.netty.maxDirectMemory) reached on HTTP/3 listener on:{}- raise it with -XX:MaxDirectMemorySize or -Dio.netty.maxDirectMemory:{}")
                    .setArguments(ctx.channel().localAddress(), cause.getMessage())
            );
        } else if (cause instanceof PortUnreachableException) {
            // a client that has gone, reported by an ICMP message
            if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setMessageFormat("HTTP/3 listener on:{}found a client's port unreachable:{}")
                        .setArguments(ctx.channel().localAddress(), cause.getMessage())
                );
            }
        } else if (cause instanceof SocketException) {
            Level level = errorsWarnedOf.add(cause.getClass().getName()) ? Level.WARN : Level.DEBUG;
            if (mockServerLogger.isEnabledForInstance(level)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(level)
                        .setMessageFormat("socket error on HTTP/3 listener on:{}:{}:{}; any more of this class are logged at DEBUG")
                        .setArguments(ctx.channel().localAddress(), cause.getClass().getName(), cause.getMessage())
                );
            }
        } else if (keepsReadingAfterEveryReadError && cause instanceof IOException) {
            Level level = errorsWarnedOf.add(cause.getClass().getName() + ": " + cause.getMessage()) ? Level.WARN : Level.DEBUG;
            if (mockServerLogger.isEnabledForInstance(level)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(level)
                        .setMessageFormat("read error on HTTP/3 listener on:{}:{}:{}; any more of this error are logged at DEBUG")
                        .setArguments(ctx.channel().localAddress(), cause.getClass().getName(), cause.getMessage())
                );
            }
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception caught on HTTP/3 listener on:{}")
                    .setArguments(ctx.channel().localAddress())
                    .setThrowable(cause)
            );
        }
    }
}
