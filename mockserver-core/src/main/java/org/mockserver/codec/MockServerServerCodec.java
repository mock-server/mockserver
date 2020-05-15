package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class MockServerServerCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerRequestDecoder, MockServerHttpToNettyResponseEncoder> {
    public MockServerServerCodec(MockServerLogger mockServerLogger, boolean isSecure) {
        init(new NettyHttpToMockServerRequestDecoder(mockServerLogger, isSecure), new MockServerHttpToNettyResponseEncoder(mockServerLogger));
    }
}
