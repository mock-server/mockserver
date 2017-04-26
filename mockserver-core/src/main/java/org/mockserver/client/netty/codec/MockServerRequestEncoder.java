package org.mockserver.client.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.client.netty.codec.mappers.MockServerOutboundHttpRequestToFullHttpRequest;
import org.mockserver.model.OutboundHttpRequest;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoder extends MessageToMessageEncoder<OutboundHttpRequest> {

    @Override
    protected void encode(ChannelHandlerContext ctx, OutboundHttpRequest httpRequest, List<Object> out) {
        out.add(new MockServerOutboundHttpRequestToFullHttpRequest().mapMockServerResponseToHttpServletResponse(httpRequest));
    }

}
