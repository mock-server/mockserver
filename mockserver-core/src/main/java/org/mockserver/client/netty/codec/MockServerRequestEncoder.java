package org.mockserver.client.netty.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.mockserver.client.netty.codec.mappers.MockServerOutboundHttpRequestToFullHttpRequest;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.model.HttpRequest;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoder extends MessageToMessageEncoder<OutboundHttpRequest> {

    @Override
    protected void encode(ChannelHandlerContext ctx, OutboundHttpRequest httpRequest, List<Object> out) {
        out.add(new MockServerOutboundHttpRequestToFullHttpRequest().mapMockServerResponseToHttpServletResponse(httpRequest));
    }

}
