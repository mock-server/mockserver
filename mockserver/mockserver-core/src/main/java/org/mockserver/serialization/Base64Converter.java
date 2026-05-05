package org.mockserver.serialization;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.nio.charset.Charset;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jamesdbloom
 */
public class Base64Converter extends ObjectWithReflectiveEqualsHashCodeToString {

    private static final String BASE64_PATTERN = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$";
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    public byte[] base64StringToBytes(String data) {
        if (data == null) {
            return new byte[0];
        }
        if (!data.matches(BASE64_PATTERN)) {
            return data.getBytes(UTF_8);
        }
        return DECODER.decode(data.getBytes(UTF_8));
    }

    public String bytesToBase64String(byte[] data) {
        return bytesToBase64String(data, UTF_8);
    }

    public String bytesToBase64String(byte[] data, Charset charset) {
        return new String(ENCODER.encode(data), charset);
    }

}
