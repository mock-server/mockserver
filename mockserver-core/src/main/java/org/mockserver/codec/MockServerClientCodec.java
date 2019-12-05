package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

public class MockServerClientCodec extends CombinedChannelDuplexHandler<NettyToMockServerResponseDecoder, MockServerToNettyRequestEncoder> {

    public MockServerClientCodec(MockServerLogger mockServerLogger) {
        init(new NettyToMockServerResponseDecoder(mockServerLogger), new MockServerToNettyRequestEncoder(mockServerLogger));
    }
}
