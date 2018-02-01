package org.mockserver.url;

import org.mockserver.logging.MockServerLogger;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class URLEncoder {
    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(URLEncoder.class);
    private static final int[] urlAllowedCharacters = new int[]{'-', '.', '_', '~', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '@', '/', '?'};

    static {
        Arrays.sort(URLEncoder.urlAllowedCharacters);

        if (MOCK_SERVER_LOGGER.isEnabled(TRACE)) {
            for (int i = 0; i < urlAllowedCharacters.length; i++) {
                MOCK_SERVER_LOGGER.trace("urlAllowedCharacters[" + i + "] = " + (char) urlAllowedCharacters[i]);
            }
        }
    }

    public static String encodeURL(String input) {
        try {
            byte[] sourceBytes = URLDecoder.decode(input, StandardCharsets.UTF_8.name())
                    .getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(sourceBytes.length);
            for (byte aSource : sourceBytes) {
                int b = aSource;
                if (b < 0) {
                    b += 256;
                }

                if (b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b >= '0' && b <= '9' || Arrays.binarySearch(urlAllowedCharacters, b) >= 0) {
                    bos.write(b);
                } else {
                    bos.write('%');
                    char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                    char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                    bos.write(hex1);
                    bos.write(hex2);
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MOCK_SERVER_LOGGER.trace("Exception while decoding or encoding url [" + input + "]", e);
            return input;
        }
    }
}
