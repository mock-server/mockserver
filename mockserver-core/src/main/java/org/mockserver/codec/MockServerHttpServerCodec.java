package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @author jamesdbloom
 */
public class MockServerHttpServerCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {

    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, SocketAddress socketAddress, SSLEngine sslEngine) {
        this(mockServerLogger, isSecure, sslEngine, socketAddress instanceof InetSocketAddress ? ((InetSocketAddress) socketAddress).getPort() : null);
    }

    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, SSLEngine sslEngine, Integer port) {
        init(new NettyHttpToMockServerHttpRequestDecoder(mockServerLogger, isSecure, sslEngine, port), new MockServerHttpToNettyHttpResponseEncoder(mockServerLogger));
    }

}
