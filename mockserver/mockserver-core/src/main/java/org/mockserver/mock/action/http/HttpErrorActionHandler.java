package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;
import org.mockserver.responsewriter.RawResponseBytesEvent;
import org.mockserver.responsewriter.ResponseWrittenBeneathCodecEvent;
import org.mockserver.socket.ConnectionReset;

import java.util.concurrent.RejectedExecutionException;

/**
 * Applies an {@link HttpError} action to the underlying Netty channel: writes raw response bytes,
 * resets the request stream (HTTP/2 RST_STREAM, written here; HTTP/3 RESET_STREAM, handled by the
 * HTTP/3 response writer seam in mockserver-netty), and/or drops or resets (TCP RST) the connection.
 * <p>
 * On HTTP/1.1 it fires {@link RawResponseBytesEvent} immediately before it writes raw bytes,
 * {@link ResponseWrittenBeneathCodecEvent} immediately after, then
 * {@link HttpExchangeEndedEvent#RAW_RESPONSE_WRITTEN} as their write completes and before any stream error or drop
 * is applied, and {@link HttpExchangeEndedEvent#INSTANCE} when nothing at all is written and the connection stays
 * open. A stream error, reset or drop with no raw bytes fires nothing: the connection's state closes with it.
 *
 * @author jamesdbloom
 */
public class HttpErrorActionHandler {

    public void handle(HttpError httpError, ChannelHandlerContext ctx) {
        handle(httpError, null, ctx);
    }

    public void handle(HttpError httpError, HttpRequest request, ChannelHandlerContext ctx) {
        handle(httpError, request, ctx, () -> {
        });
    }

    /**
     * Applies the error, running {@code responseEnded} once it has: the raw bytes written, the stream reset or the
     * connection closed, or straight away when nothing is written and the connection stays open.
     */
    public void handle(HttpError httpError, HttpRequest request, ChannelHandlerContext ctx, Runnable responseEnded) {
        if (httpError.getResponseBytes() != null) {
            // write byte directly by skipping over HTTP codec
            ChannelHandlerContext httpCodecContext = ctx.pipeline().context(HttpServerCodec.class);
            if (httpCodecContext != null) {
                // The listener is registered before the write is issued, so it runs on the event loop as the
                // write completes, ahead of anything that reads the client's next request. Chaining the
                // stream error or drop on it keeps the bytes ahead of the close without blocking the caller.
                ChannelPromise written = httpCodecContext.newPromise();
                written.addListener(future -> {
                    httpCodecContext.fireUserEventTriggered(HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN);
                    responseEnded.run();
                    resetStreamOrDropConnection(httpError, request, ctx, responseEnded);
                });
                byte[] responseBytes = httpError.getResponseBytes();
                // announced in the task that issues the write, so no other write can come between the two
                Runnable announceAndWrite = () -> {
                    httpCodecContext.fireUserEventTriggered(new RawResponseBytesEvent(responseBytes.length));
                    httpCodecContext.writeAndFlush(Unpooled.wrappedBuffer(responseBytes), written);
                    httpCodecContext.fireUserEventTriggered(ResponseWrittenBeneathCodecEvent.INSTANCE);
                };
                if (httpCodecContext.executor().inEventLoop()) {
                    announceAndWrite.run();
                } else {
                    try {
                        httpCodecContext.executor().execute(announceAndWrite);
                    } catch (RejectedExecutionException eventLoopShutDown) {
                        // as a write issued off the event loop fails when its task is refused
                        written.tryFailure(eventLoopShutDown);
                    }
                }
                return;
            }
        }
        if (!resetStreamOrDropConnection(httpError, request, ctx, responseEnded)) {
            // nothing is written and the connection stays open: this exchange is abandoned
            HttpExchangeEndedEvent.fire(ctx);
            responseEnded.run();
        }
    }

    /**
     * Apply the connection reset, stream error or connection drop, returning true if one was applied, and run
     * {@code applied} once the reset or close it issued has completed.
     */
    private boolean resetStreamOrDropConnection(HttpError httpError, HttpRequest request, ChannelHandlerContext ctx, Runnable applied) {
        if (Boolean.TRUE.equals(httpError.getResetConnection())) {
            runWhenDone(ConnectionReset.reset(ctx.channel()), applied);
            return true;
        }
        if (httpError.getStreamError() != null) {
            // reset only this stream, leaving other multiplexed streams on the connection alive.
            // HTTP/3 (QuicStreamChannel) is handled earlier by the StreamErrorWriter seam in the
            // HTTP/3 response writer (mockserver-netty), so we only reach here for HTTP/2 (reset the
            // matched stream) and HTTP/1.1 (no stream concept -> fall back to dropping the connection).
            boolean reset = resetHttp2Stream(httpError.getStreamError(), request, ctx, applied);
            if (!reset) {
                // HTTP/1.1 (or HTTP/2 stream id unavailable): there is no stream to reset, so fall
                // back to the existing HttpError connection-drop behaviour.
                ctx.disconnect();
                runWhenDone(ctx.close(), applied);
            }
            return true;
        }
        if (httpError.getDropConnection() != null && httpError.getDropConnection()) {
            ctx.disconnect();
            runWhenDone(ctx.close(), applied);
            return true;
        }
        return false;
    }

    private static void runWhenDone(ChannelFuture future, Runnable runnable) {
        if (future != null) {
            future.addListener(done -> runnable.run());
        } else {
            runnable.run();
        }
    }

    /**
     * Reset the matched HTTP/2 stream with the given error code, returning true if a reset was issued, and run
     * {@code reset} once it has been written.
     * Supports both the connection-level pipeline ({@link Http2ConnectionHandler}, the default path)
     * and the multiplex pipeline (per-stream {@link Http2StreamChannel} child channels, used when gRPC
     * bidi streaming is enabled).
     */
    private boolean resetHttp2Stream(long errorCode, HttpRequest request, ChannelHandlerContext ctx, Runnable reset) {
        // Multiplex path: the request is processed on a per-stream Http2StreamChannel child channel, so
        // writing a DefaultHttp2ResetFrame on that child channel resets exactly that stream. The parent
        // Http2MultiplexHandler/Http2FrameCodec translates it into a RST_STREAM frame for the stream.
        if (ctx.channel() instanceof Http2StreamChannel) {
            runWhenDone(ctx.writeAndFlush(new DefaultHttp2ResetFrame(errorCode)), reset);
            return true;
        }
        // Connection-level path: a single Http2ConnectionHandler multiplexes all streams over one
        // channel, so resetStream(...) is targeted by stream id (carried on the request from the
        // x-http2-stream-id extension header set by InboundHttp2ToHttpAdapter).
        ChannelHandlerContext connectionHandlerContext = ctx.pipeline().context(Http2ConnectionHandler.class);
        if (connectionHandlerContext != null && request != null && request.getStreamId() != null) {
            Http2ConnectionHandler connectionHandler = (Http2ConnectionHandler) connectionHandlerContext.handler();
            runWhenDone(connectionHandler.resetStream(connectionHandlerContext, request.getStreamId(), errorCode, connectionHandlerContext.newPromise()), reset);
            connectionHandlerContext.flush();
            return true;
        }
        return false;
    }

}
