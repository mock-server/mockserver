package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import org.mockserver.codec.mappers.FullHttpResponseToMockServerResponse;
import org.mockserver.logging.MockServerLogger;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyToMockServerResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {

    private final MockServerLogger mockServerLogger;

    NettyToMockServerResponseDecoder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
        out.add(new FullHttpResponseToMockServerResponse(mockServerLogger).mapMockServerResponseToFullHttpResponse(fullHttpResponse));
    }

}
