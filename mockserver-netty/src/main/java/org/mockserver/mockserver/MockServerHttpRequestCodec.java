package org.mockserver.mockserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.mockserver.model.NettyHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ChannelHandler.Sharable
public class MockServerHttpRequestCodec extends MessageToMessageDecoder<FullHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean secure;

    public MockServerHttpRequestCodec(boolean secure) {
        this.secure = secure;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest request, List<Object> out) throws Exception {
        if (request.getDecoderResult().isSuccess()) {
            NettyHttpRequest mockServerHttpRequest = new NettyHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(), secure);
            mockServerHttpRequest.headers().add(request.headers());

            ByteBuf content = request.content();

            if (content.isReadable()) {
                mockServerHttpRequest.content(content);
            }

            if (!request.trailingHeaders().isEmpty()) {
                mockServerHttpRequest.headers().entries().addAll(request.trailingHeaders().entries());
            }
            out.add(mockServerHttpRequest);
        } else {
            ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cause.getMessage().contains("Connection reset by peer")) {
            logger.warn("Exception caught by MockServer handler closing pipeline", cause);
        }
        ctx.close();
    }
}
