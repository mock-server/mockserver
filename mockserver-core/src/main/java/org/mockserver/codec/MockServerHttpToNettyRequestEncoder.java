package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerHttpToNettyRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

    private final MockServerHttpRequestToFullHttpRequest mockServerHttpRequestToFullHttpRequest;

    MockServerHttpToNettyRequestEncoder(MockServerLogger mockServerLogger) {
        mockServerHttpRequestToFullHttpRequest = new MockServerHttpRequestToFullHttpRequest(mockServerLogger);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
        out.add(mockServerHttpRequestToFullHttpRequest.mapMockServerRequestToNettyRequest(httpRequest));
    }

}
