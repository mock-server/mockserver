package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

public class MockServerHttpClientCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpResponseDecoder, MockServerHttpToNettyHttpRequestEncoder> {

    public MockServerHttpClientCodec(MockServerLogger mockServerLogger) {
        init(new NettyHttpToMockServerHttpResponseDecoder(mockServerLogger), new MockServerHttpToNettyHttpRequestEncoder(mockServerLogger));
    }
}
