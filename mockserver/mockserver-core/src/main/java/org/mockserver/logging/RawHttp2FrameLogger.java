package org.mockserver.logging;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;

import java.util.Map;

import static org.mockserver.logging.RawNetworkTrafficLog.CONTINUATION;
import static org.mockserver.logging.RawNetworkTrafficLog.REDACTED;
import static org.mockserver.logging.RawNetworkTrafficLog.appendEscaped;
import static org.mockserver.logging.RawNetworkTrafficLog.appendHexDump;
import static org.mockserver.logging.RawNetworkTrafficLog.appendText;

/**
 * The {@code logRawNetworkTraffic} log of an HTTP/2 connection, frame by frame. A header block is compressed on the
 * wire, so its decoded headers are logged instead, with sensitive values masked. Payloads are read by index only.
 */
final class RawHttp2FrameLogger extends Http2FrameLogger {

    private final RawNetworkTrafficLog log;

    RawHttp2FrameLogger(RawNetworkTrafficLog log) {
        super(LogLevel.INFO, RawNetworkTrafficLog.class);
        this.log = log;
    }

    @Override
    public boolean isEnabled() {
        return log.isEnabled();
    }

    @Override
    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream) {
        if (isEnabled()) {
            StringBuilder out = frame(direction, ctx, "DATA").append(": stream=").append(streamId).append(" endStream=").append(endStream)
                .append(' ').append(data.readableBytes()).append(" bytes:").append(CONTINUATION);
            appendText(data, out);
            write(out);
        }
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream) {
        if (isEnabled()) {
            StringBuilder out = frame(direction, ctx, "HEADERS").append(": stream=").append(streamId).append(" endStream=").append(endStream);
            appendHeaders(headers, out);
            write(out);
        }
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        logHeaders(direction, ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) {
        if (isEnabled()) {
            write(frame(direction, ctx, "PRIORITY").append(": stream=").append(streamId).append(" dependsOn=").append(streamDependency).append(" weight=").append(weight).append(" exclusive=").append(exclusive));
        }
    }

    @Override
    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (isEnabled()) {
            write(frame(direction, ctx, "RST_STREAM").append(": stream=").append(streamId).append(" errorCode=").append(errorCode));
        }
    }

    @Override
    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        if (isEnabled()) {
            write(frame(direction, ctx, "SETTINGS").append(": ack=true"));
        }
    }

    @Override
    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (isEnabled()) {
            write(frame(direction, ctx, "SETTINGS").append(": ack=false settings=").append(settings));
        }
    }

    @Override
    public void logPing(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            write(frame(direction, ctx, "PING").append(": ack=false data=").append(data));
        }
    }

    @Override
    public void logPingAck(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            write(frame(direction, ctx, "PING").append(": ack=true data=").append(data));
        }
    }

    @Override
    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) {
        if (isEnabled()) {
            StringBuilder out = frame(direction, ctx, "PUSH_PROMISE").append(": stream=").append(streamId).append(" promisedStream=").append(promisedStreamId);
            appendHeaders(headers, out);
            write(out);
        }
    }

    @Override
    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {
        if (isEnabled()) {
            StringBuilder out = frame(direction, ctx, "GO_AWAY").append(": lastStream=").append(lastStreamId).append(" errorCode=").append(errorCode)
                .append(" debugData:").append(CONTINUATION);
            appendText(debugData, out);
            write(out);
        }
    }

    @Override
    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
        if (isEnabled()) {
            write(frame(direction, ctx, "WINDOW_UPDATE").append(": stream=").append(streamId).append(" increment=").append(windowSizeIncrement));
        }
    }

    @Override
    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf data) {
        if (isEnabled()) {
            StringBuilder out = frame(direction, ctx, "UNKNOWN").append(": type=").append(frameType & 0xFF).append(" stream=").append(streamId)
                .append(" flags=").append(flags.value()).append(' ').append(data.readableBytes()).append(" bytes:");
            appendHexDump(data, out);
            write(out);
        }
    }

    private StringBuilder frame(Direction direction, ChannelHandlerContext ctx, String type) {
        return new StringBuilder(128).append(log.side()).append(' ').append(ctx.channel()).append(' ')
            .append(direction == Direction.INBOUND ? "received" : "sent").append(' ').append(type).append(" frame");
    }

    private void appendHeaders(Http2Headers headers, StringBuilder out) {
        out.append(" headers:");
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            out.append(CONTINUATION);
            appendOnOneLine(header.getKey(), out);
            out.append(": ");
            if (log.isSensitiveHeader(header.getKey())) {
                out.append(REDACTED);
            } else {
                appendOnOneLine(header.getValue(), out);
            }
        }
    }

    // header validation is off on the server, so a value may hold a line break that would forge a log line
    private static void appendOnOneLine(CharSequence text, StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                out.append("\\n");
            } else if (c < 0x100) {
                appendEscaped((byte) c, out);
            } else {
                out.append(c);
            }
        }
    }

    private void write(StringBuilder out) {
        try {
            log.log(out.toString());
        } catch (RuntimeException ignored) {
            // a debug log must never change what the connection does
        }
    }
}
