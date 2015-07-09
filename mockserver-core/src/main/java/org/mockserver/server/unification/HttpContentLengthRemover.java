package org.mockserver.server.unification;

import com.google.common.net.HttpHeaders;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultHttpMessage;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpContentLengthRemover extends MessageToMessageEncoder<DefaultHttpMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, DefaultHttpMessage defaultHttpMessage, List out) throws Exception {
        if (defaultHttpMessage.headers().contains(HttpHeaders.CONTENT_LENGTH, "", true)) {
            defaultHttpMessage.headers().remove(HttpHeaders.CONTENT_LENGTH);
        }
        ReferenceCountUtil.retain(defaultHttpMessage);
        out.add(defaultHttpMessage);
    }
}
