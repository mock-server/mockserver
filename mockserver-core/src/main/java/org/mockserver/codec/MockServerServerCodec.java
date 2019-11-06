package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;

/**
 * @author jamesdbloom
 */
public class MockServerServerCodec extends CombinedChannelDuplexHandler<MockServerRequestDecoder, MockServerResponseEncoder> {
    public MockServerServerCodec(boolean isSecure) {
        init(new MockServerRequestDecoder(isSecure), new MockServerResponseEncoder());
    }
}
