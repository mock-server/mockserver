package org.mockserver.formatting;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

/**
 * The indenting and unquoting writers must give the same output however their input is split into writes, and
 * whichever of the {@code char[]}, {@code String} and single-character methods each piece arrives through: Jackson
 * decides the chunks, and may change how it does. The expected output comes from the whole text in one piece.
 */
public class StringFormatterChunkedWritersTest {

    private static final char[] ALPHABET = {'a', ' ', '"', '\\', '\n', '\r', '\u000b', '\u0084', '\u0085', '\u0086', '\u2028', '\u2029', 'ü'};
    private static final String[] INDENTS = {"", "  ", "    "};

    private enum Method {CHARS, STRING, CHARACTER}

    @Test
    public void indentingWriterMatchesTheRegexAtEverySplitThroughEveryMethod() throws IOException {
        Random random = new Random(1263);
        for (int iteration = 0; iteration < 400; iteration++) {
            String text = randomText(random, random.nextInt(14));
            for (String indent : INDENTS) {
                String expected = text.replaceAll("(?m)^", indent);
                for (int split = 0; split <= text.length(); split++) {
                    for (Method first : Method.values()) {
                        for (Method second : Method.values()) {
                            StringWriter target = new StringWriter();
                            Writer writer = new StringFormatter.LineIndentingWriter(target, indent);
                            write(writer, text, 0, split, first);
                            write(writer, text, split, text.length(), second);
                            assertThat("text=" + escape(text) + " split=" + split + " " + first + "," + second, target.toString(), is(expected));
                        }
                    }
                }
                assertThat("text=" + escape(text), writeInRandomPieces(random, text, indent), is(expected));
            }
        }
    }

    @Test
    public void unquotingWriterMatchesToStringAtEverySplitThroughEveryMethod() throws IOException {
        Random random = new Random(1264);
        for (int iteration = 0; iteration < 400; iteration++) {
            String body = randomText(random, random.nextInt(12));
            for (String text : new String[]{"\"" + body + "\"", body.isEmpty() ? "x" : "x" + body}) {
                String expected = text.startsWith("\"") ? text.substring(1, text.length() - 1) : text;
                for (int split = 0; split <= text.length(); split++) {
                    for (Method first : Method.values()) {
                        for (Method second : Method.values()) {
                            StringWriter target = new StringWriter();
                            StringFormatter.UnquotingWriter writer = new StringFormatter.UnquotingWriter(target);
                            write(writer, text, 0, split, first);
                            write(writer, text, split, text.length(), second);
                            writer.finish();
                            assertThat("text=" + escape(text) + " split=" + split + " " + first + "," + second, target.toString(), is(expected));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void unquotingWriterRefusesAQuotedTextThatDoesNotEndWithAQuote() throws IOException {
        for (Method method : Method.values()) {
            StringFormatter.UnquotingWriter writer = new StringFormatter.UnquotingWriter(new StringWriter());
            write(writer, "\"open", 0, 5, method);
            assertThrows(IOException.class, writer::finish);
        }
    }

    private static String writeInRandomPieces(Random random, String text, String indent) throws IOException {
        StringWriter target = new StringWriter();
        Writer writer = new StringFormatter.LineIndentingWriter(target, indent);
        int start = 0;
        while (start < text.length()) {
            int end = start + 1 + random.nextInt(text.length() - start);
            write(writer, text, start, end, Method.values()[random.nextInt(Method.values().length)]);
            start = end;
        }
        return target.toString();
    }

    // writes text[start, end) through one method, offset inside a larger array or string so offsets are exercised
    private static void write(Writer writer, String text, int start, int end, Method method) throws IOException {
        String padded = "##" + text + "##";
        switch (method) {
            case CHARS:
                writer.write(padded.toCharArray(), start + 2, end - start);
                break;
            case STRING:
                writer.write(padded, start + 2, end - start);
                break;
            default:
                for (int i = start; i < end; i++) {
                    writer.write(text.charAt(i));
                }
        }
    }

    private static String randomText(Random random, int length) {
        char[] text = new char[length];
        for (int i = 0; i < length; i++) {
            text[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(text);
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            escaped.append(c < 0x20 || c > 0x7e ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        }
        return escaped.toString();
    }
}
