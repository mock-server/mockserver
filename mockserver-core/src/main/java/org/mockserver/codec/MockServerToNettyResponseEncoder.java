package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NottableString;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;

/**
 * @author jamesdbloom
 */
public class MockServerToNettyResponseEncoder extends MessageToMessageEncoder<HttpResponse> {

    private final MockServerHttpResponseToFullHttpResponse mockServerHttpResponseToFullHttpResponse;

    public MockServerToNettyResponseEncoder(MockServerLogger mockServerLogger) {
        mockServerHttpResponseToFullHttpResponse = new MockServerHttpResponseToFullHttpResponse(mockServerLogger);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        out.add(mockServerHttpResponseToFullHttpResponse.mapMockServerResponseToNettyResponse(response));
    }

}
