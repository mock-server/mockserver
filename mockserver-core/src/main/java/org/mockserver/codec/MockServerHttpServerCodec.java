package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class MockServerHttpServerCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {
    public MockServerHttpServerCodec(MockServerLogger mockServerLogger, boolean isSecure) {
        init(new NettyHttpToMockServerHttpRequestDecoder(mockServerLogger, isSecure), new MockServerHttpToNettyHttpResponseEncoder(mockServerLogger));
    }
}
