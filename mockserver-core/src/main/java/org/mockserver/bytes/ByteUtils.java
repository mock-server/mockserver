package org.mockserver.bytes;

/**
 * @author jamesdbloom
 */
public class ByteUtils {

    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

    public static String printHexBinary(byte data) {
        return "" + hexCode[(data >> 4) & 0xF] + hexCode[(data & 0xF)];
    }

    public static String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }
}
