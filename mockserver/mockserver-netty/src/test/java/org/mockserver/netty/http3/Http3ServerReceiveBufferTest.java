package org.mockserver.netty.http3;

import io.netty.channel.ChannelOption;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * The HTTP/3 listener's UDP receive buffer. With the platform default, a client's ACKs and flow-control credit
 * (MAX_DATA, MAX_STREAM_DATA) arriving while MockServer sends a large response overflow it and are dropped by the
 * kernel, and a client that does not resend lost credit (curl built on OpenSSL's QUIC stack) then waits for data
 * MockServer will not send. The operating system caps the size (Linux at {@code net.core.rmem_max}), so the
 * listener must have what it grants for the size asked for, and reads many datagrams per event-loop cycle so less
 * of the buffer is needed between write bursts.
 */
public class Http3ServerReceiveBufferTest {

    private Http3Server server;

    @After
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void shouldGiveTheListenerTheReceiveBufferTheOperatingSystemGrantsForTheSizeAskedFor() throws Exception {
        Assume.assumeTrue("native QUIC not available on this platform", Http3Server.isQuicAvailable());
        int platformDefault;
        int granted;
        try (DatagramChannel probe = DatagramChannel.open()) {
            platformDefault = probe.getOption(StandardSocketOptions.SO_RCVBUF);
            probe.setOption(StandardSocketOptions.SO_RCVBUF, Http3Server.RECEIVE_BUFFER_BYTES);
            granted = probe.getOption(StandardSocketOptions.SO_RCVBUF);
        }
        assertThat("this platform grants more than its default for the size asked for", granted, greaterThan(platformDefault));
        Configuration configuration = configuration();
        // no request pipeline: only the listener is under test
        server = new Http3Server(configuration, new MockServerLogger(configuration, Http3ServerReceiveBufferTest.class), null, null);
        Http3TestServer.startWithHttp3(server);

        assertThat(server.listener().config().getOption(ChannelOption.SO_RCVBUF), is(granted));
    }

    @Test
    public void shouldReadUpTo256DatagramsPerEventLoopCycle() throws Exception {
        Assume.assumeTrue("native QUIC not available on this platform", Http3Server.isQuicAvailable());
        Configuration configuration = configuration();
        server = new Http3Server(configuration, new MockServerLogger(configuration, Http3ServerReceiveBufferTest.class), null, null);
        Http3TestServer.startWithHttp3(server);

        RecvByteBufAllocator allocator = server.listener().config().getRecvByteBufAllocator();
        assertThat(allocator, instanceOf(MaxMessagesRecvByteBufAllocator.class));
        assertThat(((MaxMessagesRecvByteBufAllocator) allocator).maxMessagesPerRead(), is(Http3Server.MAX_DATAGRAMS_PER_READ));
        assertThat(Http3Server.MAX_DATAGRAMS_PER_READ, is(256));
    }
}
