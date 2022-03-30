package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;

public class MockServerBinaryClientCodec extends CombinedChannelDuplexHandler<NettyBinaryToMockServerBinaryResponseDecoder, MockServerBinaryToNettyBinaryRequestEncoder> {

    public MockServerBinaryClientCodec() {
        init(new NettyBinaryToMockServerBinaryResponseDecoder(), new MockServerBinaryToNettyBinaryRequestEncoder());
    }

}
