package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class MockServerHttpToNettyHttpRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

    private final MockServerHttpRequestToFullHttpRequest mockServerHttpRequestToFullHttpRequest;

    MockServerHttpToNettyHttpRequestEncoder(MockServerLogger mockServerLogger, Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
        mockServerHttpRequestToFullHttpRequest = new MockServerHttpRequestToFullHttpRequest(mockServerLogger, proxyConfigurations);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
        out.add(mockServerHttpRequestToFullHttpRequest.mapMockServerRequestToNettyRequest(httpRequest));
    }

}
