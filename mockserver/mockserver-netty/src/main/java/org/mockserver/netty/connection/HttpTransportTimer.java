package org.mockserver.netty.connection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.LastHttpContent;
import org.mockserver.metrics.Metrics;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;

/**
 * Times each HTTP/1.1 exchange on a connection for {@code mock_server_request_transport_duration_seconds}:
 * from the request head being decoded to the write of the last part of its response completing on the
 * socket. Unlike the handler timer it includes aggregation, event-loop hand-off of a response written from
 * another thread, encoding, and a slow reader holding the write back.
 * <p>
 * Installed after {@code HttpServerCodec}, ahead of every handler that aggregates or answers, one instance per connection, only when metrics are
 * enabled. Requests are paired with responses in arrival order, as HTTP/1.1 requires, so pipelined
 * requests are timed separately. A {@code 1xx} response other than {@code 101} precedes the real response
 * and ends nothing; a {@code 101} turns the connection into a WebSocket and the timer removes itself.
 * An exchange that ends without a {@code LastHttpContent} passing through (a raw-bytes {@code HttpError},
 * an abandoned exchange, a final {@code 1xx}) is announced by {@link HttpExchangeEndedEvent}: its start is
 * dropped unrecorded, so later exchanges on that keep-alive connection are still timed from their own start.
 * <p>
 * With its {@link #readMarker() read marker} added before the codec, it also times the span before that start for
 * {@code mock_server_request_read_to_decode_duration_seconds}: from the read that delivered a request's first bytes
 * (the first read after the previous request's last content was decoded, or the read that decoded it when the
 * next request follows in the same bytes) to the head being decoded. That covers the handlers before the codec, the
 * decode, a head split across reads, and pipelined requests ahead of it in the same read being handled first.
 */
public final class HttpTransportTimer extends ChannelDuplexHandler {

    private final ReadMarker readMarker = new ReadMarker();
    private long[] startNanos = new long[2];
    private int head;
    private int size;
    private boolean informationalResponseHeadWritten;
    // confined to the channel's event loop, like the queue above
    private boolean readSeen;
    private long lastReadNanos;
    private boolean awaitingRequest = true;
    private boolean requestReadPending;
    private long requestReadNanos;

    /**
     * The handler to add before the HTTP server codec, so the read-to-decode span starts at the read.
     */
    public ChannelHandler readMarker() {
        return readMarker;
    }

    /**
     * Removes the timer and its read marker, if present, for when the connection stops carrying HTTP/1.1.
     */
    public static void removeFrom(ChannelPipeline pipeline) {
        if (pipeline.get(ReadMarker.class) != null) {
            pipeline.remove(ReadMarker.class);
        }
        if (pipeline.get(HttpTransportTimer.class) != null) {
            pipeline.remove(HttpTransportTimer.class);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            long now = System.nanoTime();
            enqueue(now);
            if (readSeen) {
                Metrics.observeRequestReadToDecodeDurationSeconds((now - (requestReadPending ? requestReadNanos : lastReadNanos)) / 1_000_000_000.0);
            }
            awaitingRequest = false;
            requestReadPending = false;
        }
        if (msg instanceof LastHttpContent) {
            awaitingRequest = true;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof HttpExchangeEndedEvent && size > 0) {
            dequeue();
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        boolean switchingProtocols = false;
        if (msg instanceof HttpResponse) {
            HttpResponseStatus status = ((HttpResponse) msg).status();
            informationalResponseHeadWritten = status.codeClass() == HttpStatusClass.INFORMATIONAL;
            switchingProtocols = status.code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code();
        }
        if (msg instanceof LastHttpContent) {
            if (informationalResponseHeadWritten) {
                informationalResponseHeadWritten = false;
            } else if (size > 0) {
                long start = dequeue();
                promise = promise.unvoid();
                promise.addListener(future -> {
                    if (future.isSuccess()) {
                        Metrics.observeRequestTransportDurationSeconds((System.nanoTime() - start) / 1_000_000_000.0);
                    }
                });
            }
        }
        ctx.write(msg, promise);
        if (switchingProtocols) {
            size = 0;
            removeFrom(ctx.pipeline());
        }
    }

    private void onRead() {
        long now = System.nanoTime();
        readSeen = true;
        lastReadNanos = now;
        if (awaitingRequest && !requestReadPending) {
            requestReadPending = true;
            requestReadNanos = now;
        }
    }

    private void enqueue(long nanos) {
        if (size == startNanos.length) {
            long[] grown = new long[size * 2];
            for (int i = 0; i < size; i++) {
                grown[i] = startNanos[(head + i) % size];
            }
            startNanos = grown;
            head = 0;
        }
        startNanos[(head + size) % startNanos.length] = nanos;
        size++;
    }

    private long dequeue() {
        long nanos = startNanos[head];
        head = (head + 1) % startNanos.length;
        size--;
        return nanos;
    }

    private final class ReadMarker extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                onRead();
            }
            ctx.fireChannelRead(msg);
        }
    }
}
