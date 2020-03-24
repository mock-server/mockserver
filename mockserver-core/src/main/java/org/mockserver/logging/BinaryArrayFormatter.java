package org.mockserver.logging;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.codec.binary.Hex;

import java.util.Base64;

import static org.mockserver.character.Character.NEW_LINE;

public class BinaryArrayFormatter {

    public static String byteArrayToString(byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            return "base64:" + NEW_LINE + "  " + Joiner.on("\n  ").join(Splitter
                .fixedLength(64)
                .split(Base64.getEncoder().encodeToString(bytes))) + NEW_LINE +
                "hex:" + NEW_LINE + "  " + Joiner.on("\n  ").join(Splitter
                .fixedLength(64)
                .split(String.valueOf(Hex.encodeHex(bytes))));
        } else {
            return "base64:" + NEW_LINE + NEW_LINE +
                "hex:" + NEW_LINE;
        }
    }

}
