package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.model.HttpResponse;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerHttpToNettyHttpResponseEncoder extends MessageToMessageEncoder<HttpResponse> {

    private final MockServerHttpResponseToFullHttpResponse mockServerHttpResponseToFullHttpResponse;

    public MockServerHttpToNettyHttpResponseEncoder(MockServerLogger mockServerLogger) {
        mockServerHttpResponseToFullHttpResponse = new MockServerHttpResponseToFullHttpResponse(mockServerLogger);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        out.addAll(mockServerHttpResponseToFullHttpResponse.mapMockServerResponseToNettyResponse(response));
    }

}
