package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import org.mockserver.mappers.FullHttpResponseToMockServerHttpResponse;
import org.mockserver.logging.MockServerLogger;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {

    private final FullHttpResponseToMockServerHttpResponse fullHttpResponseToMockServerResponse;

    NettyHttpToMockServerHttpResponseDecoder(MockServerLogger mockServerLogger) {
        fullHttpResponseToMockServerResponse = new FullHttpResponseToMockServerHttpResponse(mockServerLogger);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
        out.add(fullHttpResponseToMockServerResponse.mapFullHttpResponseToMockServerResponse(fullHttpResponse));
    }

}
