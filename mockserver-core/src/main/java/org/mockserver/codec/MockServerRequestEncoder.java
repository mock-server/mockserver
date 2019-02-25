package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.mockserver.codec.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.model.HttpRequest;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
        out.add(new MockServerHttpRequestToFullHttpRequest().mapMockServerResponseToHttpServletResponse(httpRequest));
    }

}
