package org.mockserver.httpclient;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.logging.RawNetworkTrafficLog;
import org.mockserver.model.Protocol;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * logRawNetworkTraffic on a forward connection: the logger goes in front of the HTTP/1.1 codec (after TLS, when there
 * is TLS) only when the log is on.
 */
public class HttpClientInitializerRawNetworkTrafficTest {

    @Test
    public void shouldAddNoLoggerWhenOff() {
        NioSocketChannel channel = new NioSocketChannel();
        try {
            // as NettyHttpClient builds it from its configuration
            RawNetworkTrafficLog off = RawNetworkTrafficLog.forConfiguration(configuration().logRawNetworkTraffic(false), RawNetworkTrafficLog.FORWARD_SIDE);
            new HttpClientInitializer(null, new MockServerLogger(), false, null, Protocol.HTTP_1_1, configuration(), off).initChannel(channel);

            assertThat(channel.pipeline().names(), not(hasItem(RawNetworkTrafficLog.HANDLER_NAME)));
        } finally {
            channel.unsafe().closeForcibly();
        }
    }

    @Test
    public void shouldAddTheLoggerBeforeTheCodecWhenOn() {
        RawNetworkTrafficLog log = RawNetworkTrafficLog.forConfiguration(configuration().logRawNetworkTraffic(true), RawNetworkTrafficLog.FORWARD_SIDE);
        NioSocketChannel channel = new NioSocketChannel();
        try {
            new HttpClientInitializer(null, new MockServerLogger(), false, null, Protocol.HTTP_1_1, configuration(), log).initChannel(channel);

            List<String> names = channel.pipeline().names();
            assertThat(names, hasItem(RawNetworkTrafficLog.HANDLER_NAME));
            assertThat(names.indexOf(RawNetworkTrafficLog.HANDLER_NAME), lessThan(names.indexOf(channel.pipeline().context(HttpClientCodec.class).name())));
        } finally {
            channel.unsafe().closeForcibly();
        }
    }
}
