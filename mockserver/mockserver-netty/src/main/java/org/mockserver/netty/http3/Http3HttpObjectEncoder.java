package org.mockserver.netty.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.Locale;
import java.util.Map;

/**
 * Translates the HTTP/1.1 response objects an action writes straight to the channel (an SSE or a streamed LLM
 * response, see {@code HttpSseResponseActionHandler}) into HTTP/3 frames: the response head into a HEADERS frame,
 * each content into a DATA frame, and the last content into its DATA (and trailing HEADERS) frame followed by the
 * end of the stream. Anything else, such as the frames {@link Http3ResponseWriter} writes, is passed on unchanged.
 * <p>
 * {@link Http3ResponseWriter#respondingDirectly()} adds it ahead of the request's handler for any action that writes
 * straight to the channel (SSE, a streamed LLM response, a gRPC stream answered through the generic writer, an error
 * action), so no other HTTP/3 request, and no HTTP/1.1 or HTTP/2 request, passes through it. It holds no state.
 */
@ChannelHandler.Sharable
final class Http3HttpObjectEncoder extends ChannelOutboundHandlerAdapter {

    static final Http3HttpObjectEncoder INSTANCE = new Http3HttpObjectEncoder();
    static final String NAME = "Http3HttpObjectEncoder";

    private Http3HttpObjectEncoder() {
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof HttpObject)) {
            ctx.write(msg, promise);
            return;
        }
        if (msg instanceof HttpResponse) {
            ChannelPromise headersPromise = msg instanceof HttpContent ? ctx.newPromise() : promise;
            ctx.write(toHeadersFrame((HttpResponse) msg), headersPromise);
        }
        if (msg instanceof LastHttpContent) {
            writeLast(ctx, (LastHttpContent) msg, promise);
        } else if (msg instanceof HttpContent) {
            ctx.write(new DefaultHttp3DataFrame(((HttpContent) msg).content()), promise);
        } else if (!(msg instanceof HttpResponse)) {
            ReferenceCountUtil.release(msg);
            promise.tryFailure(new UnsupportedMessageTypeException(msg, HttpResponse.class, HttpContent.class));
        }
    }

    /**
     * Ends the stream once the last frame is written, so the FIN never overtakes earlier frames still queued, and
     * completes {@code promise} only then, so whoever ends the response afterwards finds the stream ended.
     */
    private static void writeLast(ChannelHandlerContext ctx, LastHttpContent last, ChannelPromise promise) {
        DefaultHttp3HeadersFrame trailers = null;
        if (!last.trailingHeaders().isEmpty()) {
            trailers = new DefaultHttp3HeadersFrame();
            for (Map.Entry<String, String> trailer : last.trailingHeaders()) {
                trailers.headers().add(trailer.getKey().toLowerCase(Locale.ROOT), trailer.getValue());
            }
        }
        Object lastFrame;
        if (last.content().isReadable()) {
            lastFrame = new DefaultHttp3DataFrame(last.content());
            if (trailers != null) {
                ctx.write(lastFrame, ctx.newPromise());
                lastFrame = trailers;
            }
        } else {
            ReferenceCountUtil.release(last);
            lastFrame = trailers != null ? trailers : new DefaultHttp3DataFrame(Unpooled.EMPTY_BUFFER);
        }
        ChannelPromise written = ctx.newPromise();
        ctx.write(lastFrame, written);
        written.addListener(future -> {
            if (!future.isSuccess()) {
                promise.tryFailure(future.cause());
            } else if (ctx.channel() instanceof QuicStreamChannel) {
                ((QuicStreamChannel) ctx.channel()).shutdownOutput().addListener(shutdown -> {
                    if (shutdown.isSuccess()) {
                        promise.trySuccess();
                    } else {
                        promise.tryFailure(shutdown.cause());
                    }
                });
            } else {
                promise.trySuccess();
            }
        });
    }

    /**
     * The response head as a HEADERS frame, without the fields RFC 9114 section 4.2 forbids and without
     * {@code content-length}, as for every other streamed HTTP/3 response.
     */
    static DefaultHttp3HeadersFrame toHeadersFrame(HttpResponse response) {
        DefaultHttp3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        headersFrame.headers().status(response.status().codeAsText());
        headersFrame.headers().add("server", "mockserver-http3");
        for (Map.Entry<String, String> header : response.headers()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (!Http3RequestBridge.isForbiddenHttp3ResponseHeader(name, header.getValue()) && !"content-length".equals(name)) {
                headersFrame.headers().add(name, header.getValue());
            }
        }
        return headersFrame;
    }
}
