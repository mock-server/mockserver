package org.mockserver.socket;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ConnectionResetTest {

    @Test
    public void shouldRecognisePeerResetOnlyFromAConnectionResetReadFailure() {
        assertThat(ConnectionReset.isPeerReset(new SocketException("Connection reset")), is(true));
        assertThat(ConnectionReset.isPeerReset(new IOException("readAddress(..) failed: Connection reset by peer")), is(true));
        assertThat(ConnectionReset.isPeerReset(new RuntimeException("wrapped", new SocketException("Connection reset"))), is(true));
        assertThat(ConnectionReset.isPeerReset(new IOException("Broken pipe")), is(false));
        assertThat(ConnectionReset.isPeerReset(new ClosedChannelException()), is(false));
        assertThat(ConnectionReset.isPeerReset(new IllegalStateException("connection reset")), is(false));
    }

    @Test
    public void shouldRunTheConnectionsOnResetHookBeforeClosingIt() {
        // given
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        AtomicBoolean openWhenHookRan = new AtomicBoolean();
        channel.attr(ConnectionReset.ON_RESET).set(() -> openWhenHookRan.set(channel.isOpen()));

        // when
        ConnectionReset.reset(channel);

        // then
        assertThat(openWhenHookRan.get(), is(true));
        assertThat(channel.isOpen(), is(false));
    }
}
