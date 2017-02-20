package org.mockserver.client.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.client.netty.codec.mappers.FullHttpResponseToMockServerResponse;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * @author jamesdbloom
 */
public class MockServerResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
        out.add(new FullHttpResponseToMockServerResponse().mapMockServerResponseToHttpServletResponse(fullHttpResponse));
    }

}
