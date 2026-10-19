package org.mockserver.formatting;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Action;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class StringFormatter {

    private static final Map<Integer, String> INDENTS = new HashMap<>();
    private static final Splitter fixedLengthSplitter = Splitter.fixedLength(64);
    private static final Joiner newLineJoiner = Joiner.on(NEW_LINE);
    // compiled once; String.split(String) would compile it on every call
    private static final Pattern ARGUMENT_PLACEHOLDER = Pattern.compile("\\{}");
    private static final ClassValue<Boolean> TO_STRING_IS_JSON = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("toString").getDeclaringClass() == ObjectWithJsonToString.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    static {
        INDENTS.put(0, "");
        INDENTS.put(1, "  ");
        INDENTS.put(2, "    ");
        INDENTS.put(3, "      ");
        INDENTS.put(4, "        ");
    }

    public static StringBuilder[] indentAndToString(final Object... objects) {
        return indentAndToString(1, objects);
    }

    public static StringBuilder[] indentAndToString(final int indent, final Object... objects) {
        final String indentation = INDENTS.get(indent);
        final StringBuilder[] indentedObjects = new StringBuilder[objects.length];
        for (int i = 0; i < objects.length; i++) {
            final String text = String.valueOf(objects[i]);
            final StringBuilder indented = new StringBuilder(text.length() + (text.length() >> 3) + 16);
            indented.append(NEW_LINE).append(NEW_LINE);
            appendIndented(indented, text, indentation);
            indentedObjects[i] = indented.append(NEW_LINE);
        }
        return indentedObjects;
    }

    /**
     * Appends {@code text} with {@code indentation} before each line, as {@code (?m)^} replaced by the indentation
     * does: before the first character and after each line terminator ({@code \r\n} counting as one), but not
     * after a terminator that ends the text, so empty text stays empty.
     */
    private static void appendIndented(final StringBuilder target, final String text, final String indentation) {
        final int length = text.length();
        if (length == 0) {
            return;
        }
        if (indentation.isEmpty()) {
            target.append(text);
            return;
        }
        target.append(indentation);
        int lineStart = 0;
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if ((c <= '\r' || c >= '\u0085') && (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029')) {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (i + 1 < length) {
                    target.append(text, lineStart, i + 1).append(indentation);
                    lineStart = i + 1;
                }
            }
        }
        target.append(text, lineStart, length);
    }

    public static String formatLogMessage(final String message, final Object... arguments) {
        return formatLogMessage(0, message, arguments);
    }

    public static String formatLogMessage(final int indent, final String message, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = indentAndToString(indent + 1, arguments);
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(message);
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(INDENTS.get(indent)).append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
            if (messagePartIndex < messageParts.length - 1) {
                logMessage.append(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    logMessage.append(" ");
                }
            }
        }
        return logMessage.toString();
    }

    /**
     * Writes to {@code writer} the text {@link #formatLogMessage(String, Object...)} returns, writing an argument whose
     * text is its JSON as it is serialised, so neither the message nor such an argument is held as a String. Where
     * formatLogMessage would show an argument that cannot be serialised by its fields, this throws an IOException.
     */
    public static void writeLogMessage(final Writer writer, final String message, final Object... arguments) throws IOException {
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(message);
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            writer.write(messageParts[messagePartIndex]);
            if (arguments.length > messagePartIndex) {
                writer.write(NEW_LINE);
                writer.write(NEW_LINE);
                writeArgument(new LineIndentingWriter(writer, INDENTS.get(1)), arguments[messagePartIndex]);
                writer.write(NEW_LINE);
            }
            if (messagePartIndex < messageParts.length - 1) {
                writer.write(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    writer.write(" ");
                }
            }
        }
    }

    private static void writeArgument(Writer writer, Object argument) throws IOException {
        if (argument != null && TO_STRING_IS_JSON.get(argument.getClass())) {
            UnquotingWriter unquoted = new UnquotingWriter(writer);
            try {
                ArgumentWriter.INSTANCE.writeValue(unquoted, argument);
            } catch (Exception e) {
                throw new IOException("could not write log message argument as JSON", e);
            }
            unquoted.finish();
        } else {
            writer.write(String.valueOf(argument));
        }
    }

    public static String formatLogMessage(final String[] messageParts, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = indentAndToString(arguments);
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > 0 &&
                formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
        }
        return logMessage.toString();
    }

    public static String formatCompactLogMessage(final String message, final Object... arguments) {
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(message);
        final StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < messageParts.length; i++) {
            String part = messageParts[i].trim();
            if (i > 0 && logMessage.length() > 0 && !part.isEmpty()) {
                logMessage.append(" ");
            }
            logMessage.append(part);
            if (arguments != null && i < arguments.length) {
                String compact = toCompactString(arguments[i]);
                if (!compact.isEmpty()) {
                    if (logMessage.length() > 0) {
                        logMessage.append(" ");
                    }
                    logMessage.append(compact);
                }
            }
        }
        return logMessage.toString();
    }

    @SuppressWarnings("rawtypes")
    public static String toCompactString(Object argument) {
        if (argument == null) {
            return "";
        }
        if (argument instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) argument;
            Integer statusCode = response.getStatusCode();
            return statusCode != null ? String.valueOf(statusCode) : "response";
        }
        if (argument instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) argument;
            String method = request.getMethod("");
            String path = request.getPath() != null ? request.getPath().getValue() : "";
            if (isNotBlank(method) && isNotBlank(path)) {
                return method + " " + path;
            } else if (isNotBlank(path)) {
                return path;
            } else if (isNotBlank(method)) {
                return method;
            }
            return "request";
        }
        if (argument instanceof Expectation) {
            return ((Expectation) argument).getId();
        }
        if (argument instanceof Action) {
            Action<?> action = (Action<?>) argument;
            Action.Type type = action.getType();
            return type != null ? type.name().toLowerCase() : "action";
        }
        String str = String.valueOf(argument);
        str = str.replaceAll("\\s*\\n\\s*", " ").trim();
        if (str.length() > 120) {
            str = str.substring(0, 117) + "...";
        }
        return str;
    }

    public static String formatBytes(byte[] bytes) {
        return newLineJoiner.join(fixedLengthSplitter.split(ByteBufUtil.hexDump(bytes)));
    }

    /**
     * As {@link #formatBytes(byte[])}, of at most the first {@code maxBytes} bytes (maxLoggedBodyBytes; 0 means
     * all), followed, when bytes were left out, by {@link #bytesLeftOut(int, int)}.
     */
    public static String formatBytes(byte[] bytes, int maxBytes) {
        if (maxBytes <= 0 || bytes.length <= maxBytes) {
            return formatBytes(bytes);
        }
        return newLineJoiner.join(fixedLengthSplitter.split(ByteBufUtil.hexDump(bytes, 0, maxBytes))) + bytesLeftOut(bytes.length, maxBytes);
    }

    /**
     * As {@link #formatBytes(byte[], int)}, of the buffer's readable bytes, read in place: neither copied nor
     * consumed.
     */
    public static String formatBytes(ByteBuf buffer, int maxBytes) {
        int length = buffer.readableBytes();
        int shown = maxBytes <= 0 ? length : Math.min(length, maxBytes);
        String formatted = newLineJoiner.join(fixedLengthSplitter.split(ByteBufUtil.hexDump(buffer, buffer.readerIndex(), shown)));
        return shown < length ? formatted + bytesLeftOut(length, shown) : formatted;
    }

    /**
     * {@code ByteBufUtil.hexDump} of at most the first {@code maxBytes} bytes (maxLoggedBodyBytes; 0 means all),
     * followed, when bytes were left out, by {@link #bytesLeftOut(int, int)}.
     */
    public static String hexDumpForLog(byte[] bytes, int maxBytes) {
        if (maxBytes <= 0 || bytes.length <= maxBytes) {
            return ByteBufUtil.hexDump(bytes);
        }
        return ByteBufUtil.hexDump(bytes, 0, maxBytes) + bytesLeftOut(bytes.length, maxBytes);
    }

    /**
     * The bytes decoded as UTF-8, of at most the first {@code maxBytes} (maxLoggedBodyBytes; 0 means all), followed,
     * when bytes were left out, by {@link #bytesLeftOut(int, int)}.
     */
    public static String utf8ForLog(byte[] bytes, int maxBytes) {
        if (maxBytes <= 0 || bytes.length <= maxBytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + bytesLeftOut(bytes.length, maxBytes);
    }

    /**
     * What follows a binary message cut to its first bytes: as a body cut by maxLoggedBodyBytes, it gives the
     * message's whole length.
     */
    static String bytesLeftOut(int length, int shown) {
        return "...(" + length + " bytes, only the first " + shown + " logged, maxLoggedBodyBytes)";
    }

    // loaded on first use: ObjectMapperFactory's own initialisation may format a log message
    private static final class ArgumentWriter {
        // the writer ObjectWithJsonToString.toString uses, leaving the target open
        private static final ObjectWriter INSTANCE = ObjectMapperFactory.createObjectMapper(true, false).without(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    /**
     * Writes each line it is given with {@code indent} before it, as a multiline {@code ^} replaced by the indent
     * does: before the first character and after each line terminator (a {@code \r\n} pair counting as one), but
     * not after a line terminator that ends the text.
     */
    private static final class LineIndentingWriter extends Writer {

        private final Writer writer;
        private final String indent;
        private boolean lineStart = true;
        private char previous;

        private LineIndentingWriter(Writer writer, String indent) {
            this.writer = writer;
            this.indent = indent;
        }

        @Override
        public void write(int c) throws IOException {
            if (lineStart && !(previous == '\r' && c == '\n')) {
                writer.write(indent);
            }
            writer.write(c);
            previous = (char) c;
            lineStart = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(chars[i]);
            }
        }

        @Override
        public void write(String text, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(text.charAt(i));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /**
     * Writes JSON as {@link ObjectWithJsonToString#toString()} gives it: a JSON string without its quotes.
     */
    private static final class UnquotingWriter extends Writer {

        private final Writer writer;
        private boolean started;
        private boolean quoted;
        private int held = -1;

        private UnquotingWriter(Writer writer) {
            this.writer = writer;
        }

        @Override
        public void write(int c) throws IOException {
            if (!started) {
                started = true;
                quoted = c == '"';
                if (quoted) {
                    return;
                }
            }
            if (quoted) {
                // the last character is the closing quote, so each is written once the next arrives
                if (held >= 0) {
                    writer.write(held);
                }
                held = c;
            } else {
                writer.write(c);
            }
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(chars[i]);
            }
        }

        @Override
        public void write(String text, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(text.charAt(i));
            }
        }

        void finish() throws IOException {
            if (quoted && held != '"') {
                throw new IOException("log message argument's JSON string did not end with a quote");
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
