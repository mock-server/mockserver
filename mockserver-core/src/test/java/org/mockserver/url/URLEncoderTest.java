package org.mockserver.url;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class URLEncoderTest {

    @Test
    public void shouldEncodeCharacters() {
        assertEquals("%7B%7D%5B%5D%5E%C2%A3%5C%7C", new URLEncoder().encodeURL("{}[]^£\\|"));
    }

    @Test
    public void shouldNotEncodeAllowedCharacters() {
        String input = "abc-xyz_123~890.!$&\'()*,;=:@/?";
        assertEquals(input, URLEncoder.encodeURL(input));
    }

    @Test
    public void shouldAllowAlreadyEncodedCharacters() {
        String input = "%7B%7D%5B%5D%5E%C2%A3%5C%7C";
        assertEquals(input, URLEncoder.encodeURL(input));
    }

    @Test
    public void shouldNotEncodeWhenExceptionDuringDecoding() {
        String input = "%{";
        assertEquals(input, URLEncoder.encodeURL(input));
    }
}
