package org.mockserver.logging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;

import static org.mockserver.logging.RawNetworkTrafficLog.CONTINUATION;
import static org.mockserver.logging.RawNetworkTrafficLog.MAX_BYTES_SHOWN;
import static org.mockserver.logging.RawNetworkTrafficLog.REDACTED;
import static org.mockserver.logging.RawNetworkTrafficLog.appendEscaped;
import static org.mockserver.logging.RawNetworkTrafficLog.appendHexDump;
import static org.mockserver.logging.RawNetworkTrafficLog.appendNotShown;

/**
 * Logs each message a connection reads and writes, then passes it on unchanged. Messages are read by index only:
 * this handler never retains, releases or moves the indexes of a buffer. One instance per connection, as each
 * direction keeps where it is in a header line across reads.
 */
final class RawNetworkTrafficHandler extends ChannelDuplexHandler {

    private final RawNetworkTrafficLog log;
    private final boolean binary;
    private final HeaderMasker received;
    private final HeaderMasker sent;

    RawNetworkTrafficHandler(RawNetworkTrafficLog log, boolean binary) {
        this.log = log;
        this.binary = binary;
        this.received = binary ? null : new HeaderMasker(log);
        this.sent = binary ? null : new HeaderMasker(log);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logMessage(ctx, "received", msg, received);
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logMessage(ctx, "sent", msg, sent);
        super.write(ctx, msg, promise);
    }

    private void logMessage(ChannelHandlerContext ctx, String direction, Object msg, HeaderMasker masker) {
        try {
            if (!log.isEnabled()) {
                return;
            }
            ByteBuf buf = msg instanceof ByteBuf ? (ByteBuf) msg : msg instanceof ByteBufHolder ? ((ByteBufHolder) msg).content() : null;
            StringBuilder out = new StringBuilder(128);
            out.append(log.side()).append(' ').append(ctx.channel()).append(' ').append(direction).append(' ');
            if (buf != null) {
                out.append(buf.readableBytes()).append(" bytes:");
                if (binary) {
                    appendHexDump(buf, out);
                } else {
                    out.append(CONTINUATION);
                    masker.append(buf, out);
                }
            } else if (msg instanceof FileRegion) {
                out.append("a file region of ").append(((FileRegion) msg).count()).append(" bytes (not shown)");
            } else {
                out.append(msg.getClass().getSimpleName()).append(" (not shown)");
            }
            log.log(out.toString());
        } catch (RuntimeException ignored) {
            // a debug log must never change what the connection does
        }
    }

    /**
     * Copies HTTP/1.1 text, masking the value of each sensitive header (and of its folded continuation lines).
     * Where a line is cut across reads is remembered, so a header split over two reads is still masked.
     */
    static final class HeaderMasker {

        private static final int MAX_HEADER_NAME_LENGTH = 256;

        private enum State {NAME, AFTER_NAME, COPY, MASK}

        private final RawNetworkTrafficLog log;
        private final StringBuilder name = new StringBuilder();
        private int nameCharactersShown;
        private State state = State.NAME;
        private boolean lastHeaderSensitive;
        private boolean nameBeforeWhitespaceSensitive;

        HeaderMasker(RawNetworkTrafficLog log) {
            this.log = log;
        }

        void append(ByteBuf buf, StringBuilder out) {
            int start = buf.readerIndex();
            int length = buf.readableBytes();
            int shown = Math.min(length, MAX_BYTES_SHOWN);
            for (int i = start; i < start + shown; i++) {
                next(buf.getByte(i), out);
            }
            // a header name cut by the end of what is shown is shown as far as it goes
            out.append(name, nameCharactersShown, name.length());
            nameCharactersShown = name.length();
            for (int i = start + shown; i < start + length; i++) {
                next(buf.getByte(i), null);
            }
            appendNotShown(length - shown, out);
        }

        private void next(byte b, StringBuilder out) {
            switch (state) {
                case NAME:
                    if (b == ':' && name.length() > 0) {
                        boolean sensitive = log.isSensitiveHeader(name);
                        flushName(out);
                        separator(sensitive, b, out);
                    } else if ((b == ' ' || b == '\t') && name.length() > 0) {
                        // the HTTP/1.1 decoder also takes "Name :" as a header name, so the colon may still follow
                        nameBeforeWhitespaceSensitive = log.isSensitiveHeader(name);
                        flushName(out);
                        emit(b, out);
                        state = State.AFTER_NAME;
                    } else if (name.length() == 0 && (b == ' ' || b == '\t') && lastHeaderSensitive) {
                        // a folded continuation of a sensitive header's value
                        emit(b, out);
                        if (out != null) {
                            out.append(REDACTED);
                        }
                        state = State.MASK;
                    } else if (isTokenCharacter(b) && name.length() < MAX_HEADER_NAME_LENGTH) {
                        name.append((char) b);
                    } else {
                        // a name cut short by a byte no header name has, or too long: masked if it starts like a sensitive one
                        boolean sensitive = name.length() > 0 && b != '\n' && b != '\r' && log.isSensitiveHeader(name);
                        flushName(out);
                        if (sensitive) {
                            maskRestOfLine(out);
                        } else {
                            other(b, out);
                        }
                    }
                    break;
                case AFTER_NAME:
                    if (b == ':') {
                        separator(nameBeforeWhitespaceSensitive, b, out);
                    } else if (b == ' ' || b == '\t') {
                        emit(b, out);
                    } else if (nameBeforeWhitespaceSensitive && b != '\n' && b != '\r') {
                        // Netty's decoder reads "Set-Cookie junk: value" as Set-Cookie, so the rest of the line is masked
                        maskRestOfLine(out);
                    } else {
                        other(b, out);
                    }
                    break;
                case COPY:
                    emit(b, out);
                    if (b == '\n') {
                        state = State.NAME;
                    }
                    break;
                case MASK:
                    if (b == '\n' || b == '\r') {
                        emit(b, out);
                        if (b == '\n') {
                            state = State.NAME;
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private void separator(boolean sensitive, byte colon, StringBuilder out) {
            lastHeaderSensitive = sensitive;
            emit(colon, out);
            if (sensitive) {
                if (out != null) {
                    out.append(' ').append(REDACTED);
                }
                state = State.MASK;
            } else {
                state = State.COPY;
            }
        }

        private void maskRestOfLine(StringBuilder out) {
            lastHeaderSensitive = true;
            if (out != null) {
                out.append(REDACTED);
            }
            state = State.MASK;
        }

        private void other(byte b, StringBuilder out) {
            lastHeaderSensitive = false;
            emit(b, out);
            state = b == '\n' ? State.NAME : State.COPY;
        }

        private void flushName(StringBuilder out) {
            if (out != null) {
                out.append(name, nameCharactersShown, name.length());
            }
            name.setLength(0);
            nameCharactersShown = 0;
        }

        private static void emit(byte b, StringBuilder out) {
            if (out != null) {
                appendEscaped(b, out);
            }
        }

        private static boolean isTokenCharacter(byte b) {
            return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                || b == '-' || b == '_' || b == '.' || b == '!' || b == '#' || b == '$' || b == '%' || b == '&'
                || b == '\'' || b == '*' || b == '+' || b == '^' || b == '`' || b == '|' || b == '~';
        }
    }
}
