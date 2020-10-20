package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author jamesdbloom
 */
public class MockServerHttpServerCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {
    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, SocketAddress socketAddress) {
        this(mockServerLogger, isSecure, socketAddress instanceof InetSocketAddress ? ((InetSocketAddress) socketAddress).getPort() : null);
    }

    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, Integer port) {
        init(new NettyHttpToMockServerHttpRequestDecoder(mockServerLogger, isSecure, port), new MockServerHttpToNettyHttpResponseEncoder(mockServerLogger));
    }
}
