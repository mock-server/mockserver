package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import org.mockserver.mappers.FullHttpResponseToMockServerResponse;
import org.mockserver.logging.MockServerLogger;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyToMockServerResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {

    private final FullHttpResponseToMockServerResponse fullHttpResponseToMockServerResponse;

    NettyToMockServerResponseDecoder(MockServerLogger mockServerLogger) {
        fullHttpResponseToMockServerResponse = new FullHttpResponseToMockServerResponse(mockServerLogger);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
        out.add(fullHttpResponseToMockServerResponse.mapFullHttpResponseToMockServerResponse(fullHttpResponse));
    }

}
