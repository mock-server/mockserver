package org.mockserver.client.serialization;

import javax.xml.bind.DatatypeConverter;

/**
 * @author jamesdbloom
 */
public class Base64Converter {

    private static final String BASE64_PATTERN = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$";

    public static byte[] base64StringToBytes(String data) {
        if (data.matches(BASE64_PATTERN)) {
            return DatatypeConverter.parseBase64Binary(data);
        }
        return data.getBytes();
    }

    public static String stringToBase64Bytes(byte[] data) {
        return DatatypeConverter.printBase64Binary(data);
    }
}
