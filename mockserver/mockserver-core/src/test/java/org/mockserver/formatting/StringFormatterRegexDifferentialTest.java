package org.mockserver.formatting;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * Differential test for the precompiled indentation and placeholder patterns in {@link StringFormatter}:
 * every formatting entry point must produce exactly what the previous per-call
 * {@code String.replaceAll("(?m)^", ..)} / {@code String.split("\\{}")} implementation produced.
 *
 * <p>The inputs are adversarial in the two dimensions the patterns touch: every line terminator
 * {@code (?m)^} recognises (including {@code \r\n} as one terminator, lone {@code \r}, U+0085, U+2028,
 * U+2029) at the start, middle and end of an argument, and placeholder layouts where
 * {@code split} drops or keeps empty parts (leading, trailing, adjacent, none, only placeholders).
 */
public class StringFormatterRegexDifferentialTest {

    private static final String[] INDENTS = {"", "  ", "    ", "      ", "        "};

    private static final String[] VALUES = {
        "", "a", "\n", "\r\n", "\r", "\u0085", "\u2028", "\u2029", "\n\n", "\r\r\n",
        "line1\nline2", "line1\r\nline2", "line1\rline2", "a\u0085b\u2028c\u2029d",
        "trailing\n", "trailing\r\n", "\nleading", "{\n  \"json\" : true\n}", "x\n\n\ny", "$1 \\ back",
        "ünïcödé\nİ\r\nſ", "\n\r", "\r\r", "\u2028\n", "a\r", "\ud83d\ude00\n\ud83d\ude00", "\u000b\f\u0084\u0086\u2027\u202a"
    };

    private static final String[] MESSAGES = {
        "", "no placeholders", "{}", "{}{}", "a{}", "{}a", "a{}b{}c", "a{}{}b", "{}{}{}x", "x{}{}{}",
        "request:{}didn't match expectation:{}because:{}", "{ }", "\\{}", "a {} b", "multi\nline{}msg"
    };

    @Test
    public void formatLogMessageMatchesThePerCallRegexImplementation() {
        for (int indent = 0; indent <= 3; indent++) {
            for (String message : MESSAGES) {
                for (Object[] arguments : argumentSets()) {
                    assertThat("indent=" + indent + " message=" + escape(message) + " args=" + escape(java.util.Arrays.toString(arguments)),
                        StringFormatter.formatLogMessage(indent, message, arguments),
                        is(referenceFormatLogMessage(indent, message, arguments)));
                }
            }
        }
    }

    @Test
    public void formatLogMessageWithoutIndentMatchesThePerCallRegexImplementation() {
        for (String message : MESSAGES) {
            for (Object[] arguments : argumentSets()) {
                assertThat(StringFormatter.formatLogMessage(message, arguments), is(referenceFormatLogMessage(0, message, arguments)));
            }
        }
    }

    @Test
    public void indentAndToStringMatchesThePerCallRegexImplementation() {
        for (int indent = 0; indent <= 4; indent++) {
            for (String value : VALUES) {
                StringBuilder[] actual = StringFormatter.indentAndToString(indent, new Object[]{value, null, 42});
                StringBuilder[] expected = referenceIndentAndToString(indent, new Object[]{value, null, 42});
                for (int i = 0; i < expected.length; i++) {
                    assertThat("indent=" + indent + " value=" + escape(value), actual[i].toString(), is(expected[i].toString()));
                }
            }
        }
    }

    @Test
    public void indentAndToStringMatchesTheRegexOnRandomText() {
        // every terminator, the characters either side of the scan's range checks, a surrogate pair, and replacement-syntax characters
        char[] alphabet = {'a', ' ', '\t', '\n', '\u000b', '\f', '\r', '\u000e', '\u0084', '\u0085', '\u0086', '\u2027', '\u2028', '\u2029', '\u202a', 'ü', '\ud83d', '\ude00', '$', '\\'};
        java.util.Random random = new java.util.Random(126);
        for (int iteration = 0; iteration < 50_000; iteration++) {
            char[] text = new char[random.nextInt(24)];
            for (int i = 0; i < text.length; i++) {
                text[i] = alphabet[random.nextInt(alphabet.length)];
            }
            String value = new String(text);
            int indent = random.nextInt(INDENTS.length);
            assertThat("indent=" + indent + " value=" + escape(value),
                StringFormatter.indentAndToString(indent, new Object[]{value})[0].toString(),
                is(referenceIndentAndToString(indent, new Object[]{value})[0].toString()));
        }
    }

    @Test
    public void formatCompactLogMessageMatchesThePerCallRegexImplementation() {
        for (String message : MESSAGES) {
            for (Object[] arguments : argumentSets()) {
                assertThat("message=" + escape(message), StringFormatter.formatCompactLogMessage(message, arguments),
                    is(referenceFormatCompactLogMessage(message, arguments)));
            }
        }
    }

    @Test
    public void indentationPrefixesEveryLineAfterEachTerminator() {
        // pins the semantics independently of the reference copy: (?m)^ matches at input start and after
        // each terminator, but not at the very end of input
        assertThat(StringFormatter.indentAndToString(1, new Object[]{"a\r\nb\rc\u2028d\n"})[0].toString(),
            is(NEW_LINE + NEW_LINE + "  a\r\n  b\r  c\u2028  d\n" + NEW_LINE));
    }

    private static List<Object[]> argumentSets() {
        List<Object[]> sets = new ArrayList<>();
        sets.add(new Object[0]);
        for (String value : VALUES) {
            sets.add(new Object[]{value});
            sets.add(new Object[]{value, "second\nargument", null});
        }
        sets.add(new Object[]{null});
        sets.add(new Object[]{1, 2, 3, 4, 5});
        return sets;
    }

    // ---- the pre-change implementation, verbatim apart from inlining INDENTS ----

    private static StringBuilder[] referenceIndentAndToString(final int indent, final Object... objects) {
        final StringBuilder[] indentedObjects = new StringBuilder[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] =
                new StringBuilder(NEW_LINE)
                    .append(NEW_LINE)
                    .append(String.valueOf(objects[i]).replaceAll("(?m)^", INDENTS[indent]))
                    .append(NEW_LINE);
        }
        return indentedObjects;
    }

    private static String referenceFormatLogMessage(final int indent, final String message, final Object... arguments) {
        final StringBuilder logMessage = new StringBuilder();
        final StringBuilder[] formattedArguments = referenceIndentAndToString(indent + 1, arguments);
        final String[] messageParts = message.split("\\{}");
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(INDENTS[indent]).append(messageParts[messagePartIndex]);
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

    private static String referenceFormatCompactLogMessage(final String message, final Object... arguments) {
        final String[] messageParts = message.split("\\{}");
        final StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < messageParts.length; i++) {
            String part = messageParts[i].trim();
            if (i > 0 && logMessage.length() > 0 && !part.isEmpty()) {
                logMessage.append(" ");
            }
            logMessage.append(part);
            if (arguments != null && i < arguments.length) {
                String compact = StringFormatter.toCompactString(arguments[i]);
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

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            escaped.append(c < 0x20 || c > 0x7e ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        }
        return escaped.toString();
    }
}
