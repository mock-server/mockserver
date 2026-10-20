package org.mockserver.closurecallback.websocketregistry;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;

public class WebSocketClientRegistryReconnectTest {

    private final WebSocketClientRegistry registry = new WebSocketClientRegistry(configuration(), new MockServerLogger());
    private final String clientId = UUID.randomUUID().toString();

    private static EmbeddedChannel channel() {
        return new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    }

    private static void drain(EmbeddedChannel channel) {
        Object message;
        while ((message = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    @Test
    public void shouldReplaceAStaleConnectionWhenAClientReconnectsWithItsId() {
        EmbeddedChannel stale = channel();
        EmbeddedChannel reconnect = channel();
        try {
            registry.registerClient(clientId, stale.pipeline().firstContext());
            drain(stale);

            registry.registerClient(clientId, reconnect.pipeline().firstContext());
            drain(reconnect);

            assertThat(registry.size(), is(1));
            assertThat(reconnect.isOpen(), is(true));
        } finally {
            stale.finishAndReleaseAll();
            reconnect.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldNotUnregisterTheNewConnectionWhenTheReplacedOneCloses() {
        EmbeddedChannel stale = channel();
        EmbeddedChannel reconnect = channel();
        try {
            registry.registerClient(clientId, stale.pipeline().firstContext());
            drain(stale);
            registry.registerClient(clientId, reconnect.pipeline().firstContext());
            drain(reconnect);

            stale.close();
            registry.unregisterClient(clientId, stale);

            assertThat(registry.size(), is(1));
            assertThat(reconnect.isOpen(), is(true));
        } finally {
            stale.finishAndReleaseAll();
            reconnect.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldUnregisterTheClientWhenItsOwnConnectionCloses() {
        EmbeddedChannel connection = channel();
        try {
            registry.registerClient(clientId, connection.pipeline().firstContext());
            drain(connection);

            registry.unregisterClient(clientId, connection);

            assertThat(registry.size(), is(0));
            assertThat(connection.isOpen(), is(false));
        } finally {
            connection.finishAndReleaseAll();
        }
    }
}
