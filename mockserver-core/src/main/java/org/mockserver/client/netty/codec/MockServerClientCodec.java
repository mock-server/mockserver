package org.mockserver.client.netty.codec;

import io.netty.channel.CombinedChannelDuplexHandler;

public class MockServerClientCodec extends CombinedChannelDuplexHandler<MockServerResponseDecoder, MockServerRequestEncoder> {
    public MockServerClientCodec() {
        init(new MockServerResponseDecoder(), new MockServerRequestEncoder());
    }
}
