package org.mockserver.serialization.java;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JavaCodeTest {

    @Test
    public void shouldEscapeEveryCharacterAsEscapeJavaDoes() {
        for (int c = 0; c <= 0xFFFF; c++) {
            String alone = String.valueOf((char) c);
            assertEscapedAsEscapeJava(alone);
            assertEscapedAsEscapeJava("ab" + alone + "cd");
        }
    }

    @Test
    public void shouldEscapeEveryCharacterInOneValueAsEscapeJavaDoes() {
        StringBuilder value = new StringBuilder();
        for (int c = 0; c <= 0xFFFF; c++) {
            value.append((char) c);
        }
        assertEscapedAsEscapeJava(value.toString());
    }

    @Test
    public void shouldEscapeSurrogatesAsEscapeJavaDoes() {
        String pair = new String(Character.toChars(0x1F600));
        String[] values = {
            pair,
            "a" + pair + "b",
            pair + pair,
            pair + "\"" + pair,
            new String(Character.toChars(0x10000)) + new String(Character.toChars(0x10FFFF)),
            // unpaired: a lone high, a lone low, a low then a high, a high at the end, a high before an escaped character
            "a\uD83Db",
            "a\uDE00b",
            "\uDE00\uD83D",
            "a\uD83D",
            "\uD83D\n\uDE00",
            "\uD83Dé\uDE00",
        };
        for (String value : values) {
            assertEscapedAsEscapeJava(value);
        }
    }

    @Test
    public void shouldEscapeMixedValuesAsEscapeJavaDoes() {
        char[] alphabet = {'a', 'Z', '0', ' ', '~', 0x7F, 0x80, 0xFF, '"', '\\', '\'', '\b', '\n', '\t', '\f', '\r', 0, 0x1F,
            'é', '€', '￿', '\uD83D', '\uDE00', '\uDBFF', '\uDC00'};
        Random random = new Random(20261012L);
        for (int i = 0; i < 20_000; i++) {
            char[] value = new char[random.nextInt(24)];
            for (int j = 0; j < value.length; j++) {
                value[j] = alphabet[random.nextInt(alphabet.length)];
            }
            assertEscapedAsEscapeJava(new String(value));
        }
    }

    @Test
    public void shouldWriteAnEmptyOrMissingValueAsAppendDoes() {
        assertThat(JavaCode.toString(code -> code.appendEscaped("")), is(""));
        assertThat(JavaCode.toString(code -> code.appendEscaped(null)), is("null"));
    }

    private static void assertEscapedAsEscapeJava(String value) {
        String escaped = JavaCode.toString(code -> code.appendEscaped(value));
        String expected = StringEscapeUtils.escapeJava(value);
        if (!escaped.equals(expected)) {
            assertThat(describe(value), escaped, is(expected));
        }
    }

    private static String describe(String value) {
        StringBuilder description = new StringBuilder("escaping");
        value.chars().limit(32).forEach(c -> description.append(String.format(" %04X", c)));
        return description.toString();
    }
}
