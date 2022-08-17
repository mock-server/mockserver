package org.mockserver.codec;

import com.google.common.collect.ImmutableList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.model.Header;

import java.util.List;

public class PreserveHeadersNettyRemoves extends MessageToMessageDecoder<HttpObject> {

    private static final AttributeKey<List<Header>> PRESERVED_HEADERS = AttributeKey.valueOf("PRESERVED_HEADERS");

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject httpObject, List<Object> out) throws Exception {
        if (httpObject instanceof HttpMessage) {
            final HttpHeaders headers = ((HttpMessage) httpObject).headers();
            ctx.channel().attr(PRESERVED_HEADERS).set(ImmutableList.of(
                new Header(HttpHeaderNames.CONTENT_ENCODING.toString(), headers.getAll(HttpHeaderNames.CONTENT_ENCODING))
            ));
        }
        ReferenceCountUtil.retain(httpObject);
        out.add(httpObject);
    }

    public static List<Header> preservedHeaders(Channel channel) {
        if (channel.attr(PRESERVED_HEADERS) != null && channel.attr(PRESERVED_HEADERS).get() != null) {
            return channel.attr(PRESERVED_HEADERS).get();
        } else {
            return ImmutableList.of();
        }
    }

}
