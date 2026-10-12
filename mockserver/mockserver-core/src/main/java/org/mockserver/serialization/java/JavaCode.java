package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Base64;
import java.util.function.Consumer;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * Java source written to a {@link Writer} a piece at a time, so a large value such as a body is escaped straight
 * into the output rather than built as a String of its own first. Each method writes what
 * {@link StringBuffer#append(Object)} of the same value would.
 */
final class JavaCode {

    private final Writer writer;

    JavaCode(Writer writer) {
        this.writer = writer;
    }

    static String toString(Consumer<JavaCode> code) {
        StringWriter writer = new StringWriter();
        code.accept(new JavaCode(writer));
        return writer.toString();
    }

    JavaCode append(String text) {
        try {
            writer.write(String.valueOf(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    JavaCode append(Object value) {
        return append(String.valueOf(value));
    }

    /**
     * As {@code append(StringEscapeUtils.escapeJava(value))}, escaping {@code value} as it is written.
     */
    JavaCode appendEscaped(String value) {
        if (value == null) {
            return append((String) null);
        }
        // ESCAPE_JAVA allocates for every character it reads, so it is given only the characters it changes
        try {
            int length = value.length();
            int index = 0;
            while (index < length) {
                int start = index;
                while (index < length && unchangedByEscapeJava(value.charAt(index))) {
                    index++;
                }
                if (index > start) {
                    writer.write(value, start, index - start);
                }
                // each of these is one code point that ESCAPE_JAVA translates
                while (index < length && !unchangedByEscapeJava(value.charAt(index))) {
                    StringEscapeUtils.ESCAPE_JAVA.translate(value, index, writer);
                    index += Character.charCount(Character.codePointAt(value, index));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Whether {@link StringEscapeUtils#ESCAPE_JAVA} writes {@code c} as it is: it escapes {@code "}, {@code \}
     * and every UTF-16 unit outside 32 to 0x7f, so both halves of a surrogate pair are always given to it.
     */
    private static boolean unchangedByEscapeJava(char c) {
        return c >= ' ' && c <= 0x7f && c != '"' && c != '\\';
    }

    /**
     * As {@code append(Base64.getEncoder().encodeToString(bytes))}, encoding {@code bytes} as they are written.
     */
    JavaCode appendBase64(byte[] bytes) {
        try (OutputStream base64 = Base64.getEncoder().wrap(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                writer.write(b);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                for (int i = offset; i < offset + length; i++) {
                    writer.write(bytes[i]);
                }
            }
        })) {
            base64.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    JavaCode newLineAndIndent(int numberOfSpaces) {
        return append(NEW_LINE).append(Strings.padStart("", numberOfSpaces, ' '));
    }
}
