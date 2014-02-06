package org.mockserver.proxy.interceptor;

import com.google.common.base.Charsets;
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
public class ResponseInterceptor implements Interceptor {

    private final Decoder httpResponseDecoder = new Decoder();
    private final Encoder httpResponseEncoder = new Encoder();

    @Override
    public ByteBuf intercept(ChannelHandlerContext ctx, ByteBuf channelBuffer, Logger logger) throws Exception {
        if (true) {
            return channelBuffer;
        }
        ByteBuf channelBufferCopy = Unpooled.copiedBuffer(channelBuffer);
        logger.warn("INTERCEPTING - RESPONSE: " + channelBuffer.toString(Charsets.UTF_8));
        try {
            List<ByteBuf> allResponseRawChunks = new ArrayList<ByteBuf>();
            List<Object> responseHttpFormattedChunks = new ArrayList<Object>();
            httpResponseDecoder.callDecode(ctx, channelBufferCopy, responseHttpFormattedChunks);

            for (Object httpChunk : responseHttpFormattedChunks) {
                if (httpChunk instanceof HttpResponse) {
                    HttpResponse httpResponse = (HttpResponse) httpChunk;
                    httpResponse.headers().remove(HttpHeaders.Names.ACCEPT_ENCODING);
                    httpResponse.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
                }
                logger.warn("HTTP-FORMATTED -RESPONSE- " + httpChunk.getClass().getSimpleName() + " -- " + httpChunk);
                if (!(httpChunk instanceof LastHttpContent)) {
                    List<Object> responseRawChunks = new ArrayList<Object>();
//                    if(httpChunk instanceof DefaultHttpContent) {
//                        httpChunk = ((DefaultHttpContent)httpChunk).content();
//                    }
                    httpResponseEncoder.encode(ctx, httpChunk, responseRawChunks);
                    for (Object rawChunk : responseRawChunks) {
                        if (rawChunk instanceof ByteBuf) {
                            allResponseRawChunks.add((ByteBuf) rawChunk);
                        }
                    }
                }
            }

            return Unpooled.copiedBuffer(allResponseRawChunks.toArray(new ByteBuf[allResponseRawChunks.size()]));
        } finally {
            channelBufferCopy.release();
        }
    }

    private class Encoder extends HttpResponseEncoder {
        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
            super.encode(ctx, msg, out);
        }
    }

    private class Decoder extends HttpResponseDecoder {
        public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            super.callDecode(ctx, in, out);
        }
    }
}
