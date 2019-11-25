package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

public class MockServerClientCodec extends CombinedChannelDuplexHandler<MockServerResponseDecoder, MockServerRequestEncoder> {

    public MockServerClientCodec(MockServerLogger mockServerLogger) {
        init(new MockServerResponseDecoder(mockServerLogger), new MockServerRequestEncoder(mockServerLogger));
    }
}
