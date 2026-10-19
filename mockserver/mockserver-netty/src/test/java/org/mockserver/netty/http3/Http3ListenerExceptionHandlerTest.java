package org.mockserver.netty.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;

/**
 * What the last handler of the HTTP/3 UDP listener's pipeline logs for each kind of exception, that it closes nothing,
 * and that it passes nothing on to the end of Netty's pipeline.
 */
public class Http3ListenerExceptionHandlerTest {

    private final List<LogEntry> logged = new ArrayList<>();
    private Level logLevel = Level.DEBUG;
    private final MockServerLogger mockServerLogger = new MockServerLogger(Http3ListenerExceptionHandlerTest.class) {
        @Override
        public boolean isEnabledForInstance(Level level) {
            return isEnabled(level, logLevel);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            if (isEnabledForInstance(logEntry.getLogLevel())) {
                logged.add(logEntry);
            }
        }
    };
    private final List<Throwable> passedOn = new ArrayList<>();

    @Test
    public void shouldLogTheDirectMemoryLimitAsAnErrorAndLeaveTheListenerOpen() throws Exception {
        EmbeddedChannel listener = listener();

        listener.pipeline().fireExceptionCaught(outOfDirectMemory());

        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.ERROR));
        assertThat(logged.get(0).getMessageFormat(), startsWith("direct memory limit (io.netty.maxDirectMemory) reached on HTTP/3 listener"));
        assertThat(logged.get(0).getThrowable(), is(nullValue()));
        assertThat(listener.isOpen(), is(true));
        assertThat(passedOn, empty());
    }

    @Test
    public void shouldLogAnUnreachableClientPortAtDebugOnly() {
        EmbeddedChannel listener = listener();

        listener.pipeline().fireExceptionCaught(new PortUnreachableException("ICMP port unreachable"));

        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.DEBUG));
        assertThat(logged.get(0).getThrowable(), is(nullValue()));
        assertThat(listener.isOpen(), is(true));
        assertThat(passedOn, empty());

        logged.clear();
        logLevel = Level.INFO;
        listener.pipeline().fireExceptionCaught(new PortUnreachableException("ICMP port unreachable"));
        assertThat(logged, empty());
    }

    @Test
    public void shouldLogASocketErrorOnceForEachClassAsAWarningWithoutAStackTraceAndTheRestAtDebug() {
        // Netty keeps a datagram channel reading after a SocketException, so one may follow each datagram
        EmbeddedChannel listener = listener();

        listener.pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));
        listener.pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));
        listener.pipeline().fireExceptionCaught(new NoRouteToHostException("No route to host"));
        listener.pipeline().fireExceptionCaught(new NoRouteToHostException("No route to host"));

        assertThat(logged, hasSize(4));
        assertThat(logged.get(0).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(0).getMessageFormat(), is("socket error on HTTP/3 listener on:{}:{}:{}; any more of this class are logged at DEBUG"));
        assertThat(logged.get(0).getArguments()[1], is("java.net.SocketException"));
        assertThat(logged.get(0).getArguments()[2], is("Network is unreachable"));
        assertThat(logged.get(0).getThrowable(), is(nullValue()));
        assertThat(logged.get(1).getLogLevel(), is(Level.DEBUG));
        assertThat(logged.get(1).getThrowable(), is(nullValue()));
        assertThat(logged.get(2).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(2).getArguments()[1], is("java.net.NoRouteToHostException"));
        assertThat(logged.get(3).getLogLevel(), is(Level.DEBUG));
        assertThat(listener.isOpen(), is(true));
        assertThat(passedOn, empty());

        logged.clear();
        logLevel = Level.INFO;
        listener.pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));
        assertThat("a repeat is not shown at the default level", logged, empty());
    }

    @Test
    public void shouldLogTheFirstSocketErrorOfAClassOnEachListener() {
        listener().pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));
        listener().pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));

        assertThat(logged, hasSize(2));
        assertThat(logged.get(0).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(1).getLogLevel(), is(Level.WARN));
    }

    @Test
    public void shouldLogAnyOtherExceptionAsAnErrorWithItsCauseAndLeaveTheListenerOpen() {
        EmbeddedChannel listener = listener();
        IOException readError = new IOException("datagram read failed");

        listener.pipeline().fireExceptionCaught(readError);

        assertThat(logged, hasSize(1));
        assertThat(logged.get(0).getLogLevel(), is(Level.ERROR));
        assertThat(logged.get(0).getMessageFormat(), is("exception caught on HTTP/3 listener on:{}"));
        assertThat(logged.get(0).getThrowable(), sameInstance(readError));
        assertThat(listener.isOpen(), is(true));
        assertThat(passedOn, empty());
    }

    @Test
    public void shouldLogAnyReadErrorOnceForEachClassAndMessageOnAListenerThatKeepsReadingAfterIt() {
        EmbeddedChannel listener = listener(true);

        listener.pipeline().fireExceptionCaught(new IOException("recvmmsg(..) failed with error(-105): No buffer space available"));
        listener.pipeline().fireExceptionCaught(new IOException("recvmmsg(..) failed with error(-105): No buffer space available"));
        listener.pipeline().fireExceptionCaught(new IOException("recvmmsg(..) failed with error(-5): Input/output error"));

        assertThat(logged, hasSize(3));
        assertThat(logged.get(0).getLogLevel(), is(Level.WARN));
        assertThat(logged.get(0).getMessageFormat(), is("read error on HTTP/3 listener on:{}:{}:{}; any more of this error are logged at DEBUG"));
        assertThat(logged.get(0).getArguments()[1], is("java.io.IOException"));
        assertThat(logged.get(0).getThrowable(), is(nullValue()));
        assertThat(logged.get(1).getLogLevel(), is(Level.DEBUG));
        assertThat("another errno is warned of too", logged.get(2).getLogLevel(), is(Level.WARN));
        assertThat(listener.isOpen(), is(true));
        assertThat(passedOn, empty());
    }

    private EmbeddedChannel listener() {
        return listener(false);
    }

    private EmbeddedChannel listener(boolean keepsReadingAfterEveryReadError) {
        return new EmbeddedChannel(new Http3ListenerExceptionHandler(mockServerLogger, keepsReadingAfterEveryReadError), new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                passedOn.add(cause);
            }
        });
    }

    private static OutOfDirectMemoryError outOfDirectMemory() throws Exception {
        Constructor<OutOfDirectMemoryError> constructor = OutOfDirectMemoryError.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance("failed to allocate 16777216 byte(s) of direct memory");
    }
}
