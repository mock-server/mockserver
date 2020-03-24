package org.mockserver.logging;

import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

public class BinaryArrayFormatterTest {

    @Test
    public void shouldPrintByteArray() {
        // given
        byte[] bytes = "this is a long sentence so that I can ensure that the byte array printing does correctly wrap nicely into nice looking and pretty blocks of base64 and hex binary".getBytes(UTF_8);

        // then
        assertThat(BinaryArrayFormatter.byteArrayToString(bytes), is("base64:" + NEW_LINE +
            "  dGhpcyBpcyBhIGxvbmcgc2VudGVuY2Ugc28gdGhhdCBJIGNhbiBlbnN1cmUgdGhh" + NEW_LINE +
            "  dCB0aGUgYnl0ZSBhcnJheSBwcmludGluZyBkb2VzIGNvcnJlY3RseSB3cmFwIG5p" + NEW_LINE +
            "  Y2VseSBpbnRvIG5pY2UgbG9va2luZyBhbmQgcHJldHR5IGJsb2NrcyBvZiBiYXNl" + NEW_LINE +
            "  NjQgYW5kIGhleCBiaW5hcnk=" + NEW_LINE +
            "hex:" + NEW_LINE +
            "  746869732069732061206c6f6e672073656e74656e636520736f207468617420" + NEW_LINE +
            "  492063616e20656e737572652074686174207468652062797465206172726179" + NEW_LINE +
            "  207072696e74696e6720646f657320636f72726563746c792077726170206e69" + NEW_LINE +
            "  63656c7920696e746f206e696365206c6f6f6b696e6720616e64207072657474" + NEW_LINE +
            "  7920626c6f636b73206f662062617365363420616e64206865782062696e6172" + NEW_LINE +
            "  79"));
    }

    @Test
    public void shouldHandleNullArray() {
        // given
        byte[] bytes = null;

        // then
        assertThat(BinaryArrayFormatter.byteArrayToString(bytes), is("base64:" + NEW_LINE + NEW_LINE +
            "hex:" + NEW_LINE));
    }

    @Test
    public void shouldHandleEmptyArray() {
        // given
        byte[] bytes = new byte[0];

        // then
        assertThat(BinaryArrayFormatter.byteArrayToString(bytes), is("base64:" + NEW_LINE + NEW_LINE +
            "hex:" + NEW_LINE));
    }

}