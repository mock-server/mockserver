package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

public class MockServerClientHttpCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerResponseDecoder, MockServerHttpToNettyRequestEncoder> {

    public MockServerClientHttpCodec(MockServerLogger mockServerLogger) {
        init(new NettyHttpToMockServerResponseDecoder(mockServerLogger), new MockServerHttpToNettyRequestEncoder(mockServerLogger));
    }
}
