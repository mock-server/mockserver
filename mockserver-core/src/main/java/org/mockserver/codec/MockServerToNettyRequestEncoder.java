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
public class MockServerToNettyRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

    private final MockServerLogger mockServerLogger;

    MockServerToNettyRequestEncoder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
        out.add(new MockServerHttpRequestToFullHttpRequest(mockServerLogger).mapMockServerResquestToNettyRequest(httpRequest));
    }

}
