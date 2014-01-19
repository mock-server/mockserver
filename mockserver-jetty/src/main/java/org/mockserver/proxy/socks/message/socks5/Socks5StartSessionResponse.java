package org.mockserver.proxy.socks.message.socks5;

import java.nio.ByteBuffer;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks5StartSessionResponse extends Socks5Message {
    private final ByteBuffer messageBytes;
    private final byte[] bytes;
    private final byte version;
    private final byte authenticationMethod;

    /*
    Supported authentication methods are:
          - 0x00: No authentication
          - 0x01: GSSAPI (Generic Security Services Application Program Interface)
          - 0x02: Username/Password
          - 0x03–0x7F: methods assigned by IANA (Internet Assigned Numbers Authority)
          - 0x80–0xFE: methods reserved for private use
          - 0xFF: no acceptable method (in server response)

    SOCKS5 - servers authentication choice
    1 - 1 byte: version number 0x05
    2 - 1 byte: authentication method

    i.e: 05-00
     */

    public Socks5StartSessionResponse(byte authenticationMethod) {
        this.version = SOCKS5_VERSION;
        this.authenticationMethod = authenticationMethod;

        messageBytes = ByteBuffer.allocate(2);
        // version
        messageBytes.put(SOCKS5_VERSION);
        // authentication method
        messageBytes.put(this.authenticationMethod);

        if (messageBytes.hasArray()) {
            bytes = messageBytes.array();
        } else {
            bytes = new byte[0];
        }
    }

    public ByteBuffer messageBytes() {
        if (logger.isDebugEnabled()) {
            logger.debug("Sending start session response:\n" + this);
        }
        messageBytes.flip();
        return messageBytes;
    }

    public String toString() {
        return "" +
                "Socks5StartSessionResponse {\n" +
                "   version:                       " + version + "\n" +
                "   authenticationMethod:          " + authenticationMethod + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }

}
