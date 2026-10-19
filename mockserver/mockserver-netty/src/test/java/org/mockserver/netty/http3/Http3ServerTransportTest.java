package org.mockserver.netty.http3;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * The HTTP/3 listener's transport: Linux epoll where it is available and {@code useNativeTransport} is on, reading
 * many datagrams per {@code recvmmsg} call; NIO otherwise, as on macOS and Windows.
 */
public class Http3ServerTransportTest {

    private Http3Server server;

    @Before
    public void requireQuic() {
        Assume.assumeTrue("native QUIC not available on this platform", Http3Server.isQuicAvailable());
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void shouldListenOnEpollWithBatchedReadsWhereEpollIsAvailable() throws Exception {
        Assume.assumeTrue("epoll not available on this platform", Epoll.isAvailable());

        startWith(configuration().useNativeTransport(true));

        assertThat(server.listener(), instanceOf(EpollDatagramChannel.class));
        assertThat(server.listener().config().getOption(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE), is(2048));
        assertThat("a read buffer holds a recvmmsg batch", readBufferBytes(), is(2048 * Http3Server.EPOLL_DATAGRAMS_PER_RECVMMSG));
    }

    @Test
    public void shouldListenOnNioWhereEpollIsNotAvailable() throws Exception {
        Assume.assumeTrue("epoll available on this platform", !Epoll.isAvailable());

        startWith(configuration().useNativeTransport(true));

        assertThat(server.listener(), instanceOf(NioDatagramChannel.class));
        assertThat(readBufferBytes(), is(2048));
    }

    @Test
    public void shouldListenOnNioWhenNativeTransportIsOff() throws Exception {
        startWith(configuration().useNativeTransport(false));

        assertThat(server.listener(), instanceOf(NioDatagramChannel.class));
        assertThat(readBufferBytes(), is(2048));
    }

    @SuppressWarnings("deprecation")
    private int readBufferBytes() {
        return server.listener().config().getRecvByteBufAllocator().newHandle().guess();
    }

    private void startWith(Configuration configuration) throws Exception {
        // no request pipeline: only the listener is under test
        server = new Http3Server(configuration, new MockServerLogger(configuration, Http3ServerTransportTest.class), null, null);
        Http3TestServer.startWithHttp3(server);
    }
}
