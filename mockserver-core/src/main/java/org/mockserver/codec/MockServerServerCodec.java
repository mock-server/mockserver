package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class MockServerServerCodec extends CombinedChannelDuplexHandler<NettyToMockServerRequestDecoder, MockServerToNettyResponseEncoder> {
    public MockServerServerCodec(MockServerLogger mockServerLogger, boolean isSecure) {
        init(new NettyToMockServerRequestDecoder(mockServerLogger, isSecure), new MockServerToNettyResponseEncoder(mockServerLogger));
    }
}
