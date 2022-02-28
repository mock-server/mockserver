package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;

/**
 * @author jamesdbloom
 */
public class MockServerHttpServerCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {

    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, SocketAddress socketAddress, Certificate[] clientCertificates) {
        this(mockServerLogger, isSecure, clientCertificates, socketAddress instanceof InetSocketAddress ? ((InetSocketAddress) socketAddress).getPort() : null);
    }

    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure, Certificate[] clientCertificates, Integer port) {
        init(new NettyHttpToMockServerHttpRequestDecoder(mockServerLogger, isSecure, clientCertificates, port), new MockServerHttpToNettyHttpResponseEncoder(mockServerLogger));
    }

}
