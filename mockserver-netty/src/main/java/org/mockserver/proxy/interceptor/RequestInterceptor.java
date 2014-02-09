package org.mockserver.proxy.interceptor;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class RequestInterceptor implements Interceptor {

    private final InetSocketAddress remoteSocketAddress;
    private final Decoder httpRequestDecoder = new Decoder();
    private final Encoder httpRequestEncoder = new Encoder();

    public RequestInterceptor(InetSocketAddress remoteSocketAddress) {

        this.remoteSocketAddress = remoteSocketAddress;
    }

    @Override
    public ByteBuf intercept(ChannelHandlerContext ctx, ByteBuf channelBuffer, Logger logger) throws Exception {
        ByteBuf channelBufferCopy = Unpooled.copiedBuffer(channelBuffer);
        logger.debug("INTERCEPTING - REQUEST: " + channelBuffer.toString(Charsets.UTF_8));
        try {
            List<ByteBuf> allRequestRawChunks = new ArrayList<ByteBuf>();
            List<Object> requestHttpFormattedChunks = new ArrayList<Object>();
            httpRequestDecoder.callDecode(ctx, channelBufferCopy, requestHttpFormattedChunks);

            for (Object httpChunk : requestHttpFormattedChunks) {
                if (httpChunk instanceof HttpRequest) {
                    HttpRequest httpRequest = (HttpRequest) httpChunk;
                    httpRequest.headers().remove(HttpHeaders.Names.ACCEPT_ENCODING);
                    if (remoteSocketAddress != null) {
                        httpRequest.headers().set(HttpHeaders.Names.HOST, remoteSocketAddress.getHostName() + ":" + remoteSocketAddress.getPort());
                    }
                    httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
                }
                logger.debug("HTTP-FORMATTED -REQUEST- " + httpChunk.getClass().getSimpleName() + " -- " + httpChunk);
                if (!(httpChunk instanceof LastHttpContent)) {
                    List<Object> requestRawChunks = new ArrayList<Object>();
                    httpRequestEncoder.encode(ctx, httpChunk, requestRawChunks);
                    for (Object rawChunk : requestRawChunks) {
                        if (rawChunk instanceof ByteBuf) {
                            allRequestRawChunks.add((ByteBuf) rawChunk);
                        }
                    }
                }
            }

            return Unpooled.copiedBuffer(allRequestRawChunks.toArray(new ByteBuf[allRequestRawChunks.size()]));
        } finally {
            channelBufferCopy.release();
        }
    }


    private class Encoder extends HttpRequestEncoder {
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

    private class Decoder extends HttpRequestDecoder {
        public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            super.callDecode(ctx, in, out);
        }
    }
}
