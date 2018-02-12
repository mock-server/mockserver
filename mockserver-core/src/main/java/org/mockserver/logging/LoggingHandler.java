package org.mockserver.logging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

import static org.mockserver.character.Character.NEW_LINE;

@Sharable
public class LoggingHandler extends ChannelDuplexHandler {

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

    protected final Logger logger;

    public LoggingHandler(String loggerName) {
        logger = LoggerFactory.getLogger(loggerName);
    }

    public LoggingHandler(Logger logger) {
        this.logger = logger;
    }

    public void addLoggingHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(LoggingHandler.class) != null) {
            ctx.pipeline().remove(LoggingHandler.class);
        }
        if (ctx.pipeline().get(SslHandler.class) != null) {
            ctx.pipeline().addAfter("SslHandler#0", "LoggingHandler#0", this);
        } else {
            ctx.pipeline().addFirst(this);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        logger.trace(format(ctx, "REGISTERED"));
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.trace(format(ctx, "UNREGISTERED"));
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.trace(format(ctx, "ACTIVE"));
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.trace(format(ctx, "INACTIVE"));
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.trace(format(ctx, "EXCEPTION: " + cause), cause);
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            logger.trace(format(ctx, "SslHandshakeCompletionEvent: "), ((SslHandshakeCompletionEvent) evt).cause());
        } else if (evt instanceof Exception) {
            logger.trace(format(ctx, "Exception: "), (Exception) evt);
        } else {
            logger.trace(format(ctx, "USER_EVENT: " + evt));
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        logger.trace(format(ctx, "BIND(" + localAddress + ')'));
        super.bind(ctx, localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        logger.trace(format(ctx, "CONNECT(" + remoteAddress + ", " + localAddress + ')'));
        super.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        logger.trace(format(ctx, "DISCONNECT()"));
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        logger.trace(format(ctx, "CLOSE()"));
        super.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        logger.trace(format(ctx, "DEREGISTER()"));
        super.deregister(ctx, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logMessage(ctx, "RECEIVED", msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logMessage(ctx, "WRITE", msg);
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        logger.trace(format(ctx, "FLUSH"));
        ctx.flush();
    }

    private void logMessage(ChannelHandlerContext ctx, String eventName, Object msg) {
        logger.trace(format(ctx, formatMessage(eventName, msg)));
    }

    protected String format(ChannelHandlerContext ctx, String message) {
        String chStr = ctx.channel().toString() + ' ' + message;
        if (logger.isTraceEnabled()) {
            chStr += NEW_LINE + "channel: " + ctx.channel().id() + NEW_LINE + "pipeline: " + ctx.pipeline().names() + NEW_LINE;
        }
        return chStr;
    }

    private String formatMessage(String eventName, Object msg) {
        if (msg instanceof ByteBuf) {
            return formatByteBuf(eventName, (ByteBuf) msg);
        } else if (msg instanceof ByteBufHolder) {
            return formatByteBufHolder(eventName, (ByteBufHolder) msg);
        } else {
            return formatNonByteBuf(eventName, msg);
        }
    }

    private String formatByteBuf(String eventName, ByteBuf buf) {
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
                if (i > 15 && buf.readableBytes() > i) {
                    dump.append(buf.toString(i - 15, 16, StandardCharsets.UTF_8).replaceAll("" + NEW_LINE, "/").replaceAll("\r", "/"));
                } else {
                    for (int j = i - 15; j <= i; j++) {
                        dump.append(BYTE2CHAR[buf.getUnsignedByte(j)]);
                    }
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

    private String formatNonByteBuf(String eventName, Object msg) {
        return eventName + ": " + msg;
    }

    private String formatByteBufHolder(String eventName, ByteBufHolder msg) {
        return formatByteBuf(eventName, msg.content());
    }
}
