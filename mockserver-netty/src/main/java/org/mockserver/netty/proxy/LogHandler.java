package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.model.NettyHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChannelHandler} that logs all events using a logging framework.
 * By default, all events are logged at <tt>DEBUG</tt> level.
 */
public class LogHandler extends ChannelDuplexHandler {

    protected static final Logger logger = LoggerFactory.getLogger(LogHandler.class);
    private static final String NEWLINE = String.format("%n");
    private static final String[] BYTE2HEX = new String[256];
    private static final String[] HEXPADDING = new String[16];
    private static final String[] BYTEPADDING = new String[16];
    private static final char[] BYTE2CHAR = new char[256];

    static {
        int i;

        // Generate the lookup table for byte-to-hex-dump conversion
        for (i = 0; i < 10; i++) {
            BYTE2HEX[i] = " 0" + i;
        }
        for (; i < 16; i++) {
            BYTE2HEX[i] = " 0" + (char) ('a' + i - 10);
        }
        for (; i < BYTE2HEX.length; i++) {
            BYTE2HEX[i] = " " + Integer.toHexString(i);
        }

        // Generate the lookup table for hex dump paddings
        for (i = 0; i < HEXPADDING.length; i++) {
            int padding = HEXPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding * 3);
            for (int j = 0; j < padding; j++) {
                buf.append("   ");
            }
            HEXPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte dump paddings
        for (i = 0; i < BYTEPADDING.length; i++) {
            int padding = BYTEPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding);
            for (int j = 0; j < padding; j++) {
                buf.append(' ');
            }
            BYTEPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte-to-char conversion
        for (i = 0; i < BYTE2CHAR.length; i++) {
            if (i <= 0x1f || i >= 0x7f) {
                BYTE2CHAR[i] = '.';
            } else {
                BYTE2CHAR[i] = (char) i;
            }
        }
    }

    private ByteBuf request = Unpooled.buffer();
    private ByteBuf response = Unpooled.buffer();

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        List<Object> requestChunks = new ArrayList<Object>();
        new HttpRequestDecoder() {
            public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                super.callDecode(ctx, in, out);
            }
        }.callDecode(ctx, request, requestChunks);

        NettyHttpRequest mockServerHttpRequest = null;
        for (Object msg : requestChunks) {
            if (msg instanceof HttpObject && ((HttpObject) msg).getDecoderResult().isSuccess()) {
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    mockServerHttpRequest = new NettyHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(), false);
                    mockServerHttpRequest.headers().add(request.headers());
                }

                if (msg instanceof HttpContent && mockServerHttpRequest != null) {
                    ByteBuf content = ((HttpContent) msg).content();

                    if (content.isReadable()) {
                        mockServerHttpRequest.content(content);
                    }

                    if (msg instanceof LastHttpContent) {

                        LastHttpContent trailer = (LastHttpContent) msg;
                        if (!trailer.trailingHeaders().isEmpty()) {
                            mockServerHttpRequest.headers().entries().addAll(trailer.trailingHeaders().entries());
                        }

                    }

                }
            }
        }
//        logger.warn("MockServerHttpRequest -- " + new NettyToMockServerRequestMapper().mapNettyRequestToMockServerRequest(mockServerHttpRequest));

        List<Object> responseChunks = new ArrayList<Object>();
        new HttpResponseDecoder() {
            public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                super.callDecode(ctx, in, out);
            }
        }.callDecode(ctx, response, responseChunks);

        NettyHttpResponse mockServerHttpResponse = null;
        for (Object msg : responseChunks) {
            if (msg instanceof HttpObject && ((HttpObject) msg).getDecoderResult().isSuccess()) {
                if (msg instanceof HttpResponse) {
                    HttpResponse request = (HttpResponse) msg;
                    mockServerHttpResponse = new NettyHttpResponse(request.getProtocolVersion(), request.getStatus());
                    mockServerHttpResponse.headers().add(request.headers());
                }

                if (msg instanceof HttpContent && mockServerHttpResponse != null) {
                    ByteBuf content = ((HttpContent) msg).content();

                    if (content.isReadable()) {
                        mockServerHttpResponse.content(content);
                    }

                    if (msg instanceof LastHttpContent) {

                        LastHttpContent trailer = (LastHttpContent) msg;
                        if (!trailer.trailingHeaders().isEmpty()) {
                            mockServerHttpResponse.headers().entries().addAll(trailer.trailingHeaders().entries());
                        }

                    }

                }
            }
        }
//        logger.warn("MockServerHttpResponse -- " + new NettyToMockServerResponseMapper().mapNettyRequestToMockServerResponse(mockServerHttpResponse));

        request = Unpooled.buffer();
        response = Unpooled.buffer();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        List<ByteBuf> allRequestRawChunks = new ArrayList<ByteBuf>();
        if (msg instanceof ByteBuf) {
            List<Object> requestHttpFormattedChunks = new ArrayList<Object>();
            new HttpRequestDecoder() {
                public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                    super.callDecode(ctx, in, out);
                }
            }.callDecode(ctx, Unpooled.copiedBuffer((ByteBuf) msg), requestHttpFormattedChunks);

            for (Object httpChunk : requestHttpFormattedChunks) {
                if (httpChunk instanceof HttpRequest) {
                    HttpRequest httpRequest = (HttpRequest) httpChunk;
                    httpRequest.headers().set(HttpHeaders.Names.HOST, "www.mock-server.com");
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

            for (ByteBuf rawChunk : allRequestRawChunks) {
                logger.warn(formatByteBuf("RAW-FORMATTED", rawChunk));
            }

            request.writeBytes(Unpooled.copiedBuffer((ByteBuf) msg));
        }

        ctx.fireChannelRead(Unpooled.copiedBuffer(allRequestRawChunks.toArray(new ByteBuf[allRequestRawChunks.size()])));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
//        logger.warn(formatByteBuf("RAW-RAW-FORMATTED", (ByteBuf)msg));
//        List<ByteBuf> allResponseRawChunks = new ArrayList<ByteBuf>();
        if (msg instanceof ByteBuf) {
//            List<Object> responseHttpFormattedChunks = new ArrayList<Object>();
//            new HttpResponseDecoder() {
//                public void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
//                    super.callDecode(ctx, in, out);
//                }
//            }.callDecode(ctx, Unpooled.copiedBuffer((ByteBuf) msg), responseHttpFormattedChunks);
//
//            for (Object httpChunk : responseHttpFormattedChunks) {
//                logger.warn("HTTP-FORMATTED -- " + httpChunk.getClass().getSimpleName() + " -- " + httpChunk);
//                if (!(httpChunk instanceof LastHttpContent)) {
//                    List<Object> responseRawChunks = new ArrayList<Object>();
//                    new HttpResponseEncoder() {
//                        public void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
//                            super.encode(ctx, msg, out);
//                        }
//                    }.encode(ctx, httpChunk, responseRawChunks);
//                    for (Object rawChunk : responseRawChunks) {
//                        if (rawChunk instanceof ByteBuf) {
//                            allResponseRawChunks.add((ByteBuf) rawChunk);
//                        }
//                    }
//                }
//            }
//
//            for (ByteBuf rawChunk : allResponseRawChunks) {
//                logger.warn(formatByteBuf("RAW-FORMATTED", rawChunk));
//            }

            response.writeBytes(Unpooled.copiedBuffer((ByteBuf) msg));
        }
//        ctx.write(Unpooled.copiedBuffer(allResponseRawChunks.toArray(new ByteBuf[allResponseRawChunks.size()])), promise);
        ctx.write(msg, promise);
    }

    /**
     * Returns a String which contains all details to log the {@link ByteBuf}
     */
    protected String formatByteBuf(String eventName, ByteBuf buf) {
        int length = buf.readableBytes();
        int rows = length / 16 + (length % 15 == 0 ? 0 : 1) + 4;
        StringBuilder dump = new StringBuilder(rows * 80 + eventName.length() + 16);

        dump.append(eventName).append('(').append(length).append('B').append(')')
                .append(NEWLINE)
                .append("         +-------------------------------------------------+")
                .append(NEWLINE)
                .append("         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |")
                .append(NEWLINE)
                .append("+--------+-------------------------------------------------+----------------+");

        final int startIndex = buf.readerIndex();
        final int endIndex = buf.writerIndex();

        int i;
        for (i = startIndex; i < endIndex; i++) {
            int relIdx = i - startIndex;
            int relIdxMod16 = relIdx & 15;
            if (relIdxMod16 == 0) {
                dump.append(NEWLINE);
                dump.append(Long.toHexString(relIdx & 0xFFFFFFFFL | 0x100000000L));
                dump.setCharAt(dump.length() - 9, '|');
                dump.append('|');
            }
            dump.append(BYTE2HEX[buf.getUnsignedByte(i)]);
            if (relIdxMod16 == 15) {
                dump.append(" |");
                for (int j = i - 15; j <= i; j++) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                }
                dump.append('|');
            }
        }

        if ((i - startIndex & 15) != 0) {
            int remainder = length & 15;
            dump.append(HEXPADDING[remainder]);
            dump.append(" |");
            for (int j = i - remainder; j < i; j++) {
                dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
            }
            dump.append(BYTEPADDING[remainder]);
            dump.append('|');
        }

        dump.append(NEWLINE).append("+--------+-------------------------------------------------+----------------+");

        return dump.toString();
    }
}
