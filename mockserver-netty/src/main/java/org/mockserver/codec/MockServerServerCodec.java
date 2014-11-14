package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.client.netty.codec.MockServerRequestEncoder;
import org.mockserver.client.netty.codec.MockServerResponseDecoder;

/**
 * @author jamesdbloom
 */
public class MockServerServerCodec extends CombinedChannelDuplexHandler<MockServerRequestDecoder, MockServerResponseEncoder> {
    public MockServerServerCodec(boolean isSecure) {
        init(new MockServerRequestDecoder(isSecure), new MockServerResponseEncoder());
    }
}
