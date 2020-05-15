package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

public class MockServerClientBinaryCodec extends CombinedChannelDuplexHandler<NettyBinaryToMockServerResponseDecoder, MockServerBinaryToNettyRequestEncoder> {

    public MockServerClientBinaryCodec() {
        init(new NettyBinaryToMockServerResponseDecoder(), new MockServerBinaryToNettyRequestEncoder());
    }
}
