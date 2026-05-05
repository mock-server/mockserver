package org.mockserver.codec;

import io.netty.channel.CombinedChannelDuplexHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

import java.util.Map;

public class MockServerHttpClientCodec extends CombinedChannelDuplexHandler<NettyHttpToMockServerHttpResponseDecoder, MockServerHttpToNettyHttpRequestEncoder> {

    public MockServerHttpClientCodec(MockServerLogger mockServerLogger, Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
        init(new NettyHttpToMockServerHttpResponseDecoder(mockServerLogger), new MockServerHttpToNettyHttpRequestEncoder(mockServerLogger, proxyConfigurations));
    }

}
