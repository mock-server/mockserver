package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerRequest;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final FullHttpRequestToMockServerRequest fullHttpRequestToMockServerRequest;

    public NettyHttpToMockServerRequestDecoder(MockServerLogger mockServerLogger, boolean isSecure) {
        fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerRequest(mockServerLogger, isSecure);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
        out.add(fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest));
    }

}
