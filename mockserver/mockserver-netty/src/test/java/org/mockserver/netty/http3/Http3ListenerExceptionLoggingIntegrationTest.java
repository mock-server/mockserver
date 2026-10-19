package org.mockserver.netty.http3;

import io.netty.channel.Channel;
import io.netty.channel.epoll.EpollDatagramChannel;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.integration.NettyLogCapture;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * What MockServer logs for an exception on the HTTP/3 UDP listener, whose pipeline holds Netty's QUIC codec, which
 * takes no exception: one entry in MockServer's log, nothing through Netty's logger, and the listener left open.
 * Skips when the native QUIC transport is not available, as the other HTTP/3 server tests do.
 */
public class Http3ListenerExceptionLoggingIntegrationTest {

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private NettyLogCapture nettysLog;
    private Http3Server server;

    @Before
    public void startServer() throws Exception {
        assumeQuicAvailable();
        nettysLog = new NettyLogCapture(NettyLogCapture.PIPELINE);
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> {
            if (String.valueOf(logEntry.getMessageFormat()).contains("HTTP/3 listener")) {
                logged.add(logEntry.clone());
            }
        });
        Configuration configuration = configuration().logLevel("DEBUG");
        // no request pipeline: only the listener is under test
        server = new Http3Server(configuration, new MockServerLogger(configuration, Http3ListenerExceptionLoggingIntegrationTest.class), null, null);
        Http3TestServer.startWithHttp3(server);
        nettysLog.attach();
    }

    @After
    public void stopServer() {
        try {
            if (server != null) {
                server.stop();
            }
        } finally {
            MockServerLogger.setGlobalLogEventListener(null);
            if (nettysLog != null) {
                nettysLog.close();
            }
        }
    }

    @Test
    public void shouldLogAnUnexpectedExceptionOnceAsAnErrorWithItsCauseAndLeaveTheListenerOpen() throws Exception {
        Channel listener = server.listener();
        IllegalStateException unexpected = new IllegalStateException("datagram handling failed");

        listener.pipeline().fireExceptionCaught(unexpected);

        List<LogEntry> entries = awaitEntries();
        assertThat(nettysLog.all(), empty());
        assertThat(entries.toString(), entries, hasSize(1));
        assertThat(entries.get(0).getLogLevel(), is(Level.ERROR));
        assertThat(entries.get(0).getMessageFormat(), is("exception caught on HTTP/3 listener on:{}"));
        assertThat(entries.get(0).getArguments()[0], is(listener.localAddress()));
        assertThat(entries.get(0).getThrowable(), sameInstance(unexpected));
        assertThat("the listener is left open", listener.isOpen(), is(true));
        nettysLog.assertThatItSeesWhatNettyLogs();
    }

    @Test
    public void shouldLogAReadErrorByWhetherTheListenersTransportKeepsReadingAfterIt() throws Exception {
        Channel listener = server.listener();
        IOException readError = new IOException("datagram read failed");

        listener.pipeline().fireExceptionCaught(readError);

        List<LogEntry> entries = awaitEntries();
        assertThat(nettysLog.all(), empty());
        assertThat(entries.toString(), entries, hasSize(1));
        if (listener instanceof EpollDatagramChannel) {
            // epoll keeps reading after any read error, so it may recur
            assertThat(entries.get(0).getLogLevel(), is(Level.WARN));
            assertThat(entries.get(0).getThrowable(), is(nullValue()));
        } else {
            // NIO closes the listener after such a read error
            assertThat(entries.get(0).getLogLevel(), is(Level.ERROR));
            assertThat(entries.get(0).getThrowable(), sameInstance(readError));
        }
        nettysLog.assertThatItSeesWhatNettyLogs();
    }

    @Test
    public void shouldLogARepeatedSocketErrorAsOneWarningWithoutAStackTraceAndLeaveTheListenerOpen() throws Exception {
        Channel listener = server.listener();

        for (int i = 0; i < 3; i++) {
            listener.pipeline().fireExceptionCaught(new SocketException("Network is unreachable"));
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (logged.size() < 3 && nettysLog.size() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        List<LogEntry> entries = awaitEntries();
        assertThat(nettysLog.all(), empty());
        assertThat(entries.toString(), entries, hasSize(3));
        assertThat(entries.get(0).getLogLevel(), is(Level.WARN));
        assertThat(entries.get(0).getThrowable(), is(nullValue()));
        assertThat(entries.get(1).getLogLevel(), is(Level.DEBUG));
        assertThat(entries.get(2).getLogLevel(), is(Level.DEBUG));
        assertThat("the listener is left open", listener.isOpen(), is(true));
        nettysLog.assertThatItSeesWhatNettyLogs();
    }

    @Test
    public void shouldLogAnUnreachableClientPortOnceBelowAWarningAndLeaveTheListenerOpen() throws Exception {
        Channel listener = server.listener();

        listener.pipeline().fireExceptionCaught(new PortUnreachableException("ICMP port unreachable"));

        List<LogEntry> entries = awaitEntries();
        assertThat(nettysLog.all(), empty());
        assertThat(entries.toString(), entries, hasSize(1));
        assertThat(entries.get(0).getLogLevel(), is(Level.DEBUG));
        assertThat(entries.get(0).getMessageFormat(), is("HTTP/3 listener on:{}found a client's port unreachable:{}"));
        assertThat(entries.get(0).getThrowable(), is(nullValue()));
        assertThat("the listener is left open", listener.isOpen(), is(true));
        nettysLog.assertThatItSeesWhatNettyLogs();
    }

    private List<LogEntry> awaitEntries() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (logged.isEmpty() && nettysLog.size() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        // whatever else the same exception logs follows at once
        TimeUnit.MILLISECONDS.sleep(200);
        return logged.stream().collect(Collectors.toList());
    }

    private static void assumeQuicAvailable() {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable t) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 test", available);
    }
}
