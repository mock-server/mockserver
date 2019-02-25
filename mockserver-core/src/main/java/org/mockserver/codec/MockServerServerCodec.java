package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class MockServerServerCodec extends CombinedChannelDuplexHandler<MockServerRequestDecoder, MockServerResponseEncoder> {
    public MockServerServerCodec(MockServerLogger mockServerLogger, boolean isSecure) {
        init(new MockServerRequestDecoder(mockServerLogger, isSecure), new MockServerResponseEncoder());
    }
}
