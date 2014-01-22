package org.mockserver.proxy.socks.message.socks5;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks5StartSessionRequest extends Socks5Message {
    private final byte[] bytes;
    private final byte version;
    private final byte numberOfAuthenticationMethods;
    private final byte[] authenticationMethods;


    /*
    Supported authentication methods are:
          - 0x00: No authentication
          - 0x01: GSSAPI (Generic Security Services Application Program Interface)
          - 0x02: Username/Password
          - 0x03–0x7F: methods assigned by IANA (Internet Assigned Numbers Authority)
          - 0x80–0xFE: methods reserved for private use
          - 0xFF: no acceptable method (in server response)

    SOCKS5 - initial greeting
    1 - 1 byte: version number 0x05
    2 - 1 byte: number of authentication method supported
    3 - variable: authentication methods

    i.e: 05-01-00
     */

    public Socks5StartSessionRequest(ByteBuffer messageBytes) throws IOException {
        version = messageBytes.get();
        if (version != SOCKS5_VERSION) {
            throw new RuntimeException("Incorrect SOCKS5 version, expected [" + printHexBinary(SOCKS5_VERSION) + "] found [" + printHexBinary(version) + "]");
        }
        numberOfAuthenticationMethods = messageBytes.get();
        authenticationMethods = new byte[numberOfAuthenticationMethods];
        messageBytes.get(authenticationMethods);

        if (messageBytes.hasArray()) {
            bytes = Arrays.copyOf(messageBytes.array(), 2 + numberOfAuthenticationMethods);
        } else {
            bytes = new byte[0];
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Received start session request:\n" + this);
        }
    }

    public boolean isValid() {
        return true;
    }

    public String toString() {
        return "" +
                "Socks5StartSessionRequest {\n" +
                "   version:                       " + version + "\n" +
                "   numberOfAuthenticationMethods: " + numberOfAuthenticationMethods + "\n" +
                "   authenticationMethods:         " + printHexBinary(authenticationMethods) + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }
}
