package org.mockserver.netty.proxy.interceptor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class RequestInterceptor implements Interceptor {

    @Override
    public ByteBuf intercept(ChannelHandlerContext ctx, ByteBuf channelBuffer, Logger logger) throws Exception {
        ByteBuf channelBufferCopy = Unpooled.copiedBuffer(channelBuffer);
        try {
            List<ByteBuf> allRequestRawChunks = new ArrayList<ByteBuf>();
            List<Object> requestHttpFormattedChunks = new ArrayList<Object>();
            new HttpRequestDecoder() {
                public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                    super.callDecode(ctx, in, out);
                }
            }.callDecode(ctx, channelBufferCopy, requestHttpFormattedChunks);

            for (Object httpChunk : requestHttpFormattedChunks) {
                if (httpChunk instanceof HttpRequest) {
                    HttpRequest httpRequest = (HttpRequest) httpChunk;
                    httpRequest.headers().remove(HttpHeaders.Names.ACCEPT_ENCODING);
//                    httpRequest.headers().set(HttpHeaders.Names.HOST, "www.mock-server.com");
                    httpRequest.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
                }
                logger.warn("HTTP-FORMATTED -- " + httpChunk.getClass().getSimpleName() + " -- " + httpChunk);
                if (!(httpChunk instanceof LastHttpContent)) {
                    List<Object> requestRawChunks = new ArrayList<Object>();
                    new HttpRequestEncoder() {
                        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                            super.encode(ctx, msg, out);
                        }
                    }.encode(ctx, httpChunk, requestRawChunks);
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
}
