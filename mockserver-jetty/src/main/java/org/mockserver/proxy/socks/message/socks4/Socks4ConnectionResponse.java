package org.mockserver.proxy.socks.message.socks4;

import java.nio.ByteBuffer;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks4ConnectionResponse extends Socks4Message {
    private final ByteBuffer messageBytes;
    private final byte[] bytes;
    private final byte version;
    private final byte status;

    /*
    SOCKS4 - connection response
    1 - 1 byte: null byte
    2 - 1 byte: status byte:
                - 0x5a request granted
                - 0x5b request rejected or failed
                - 0x5c request rejected failed because client is not running identd (Identification Protocol Daemon) (or not reached from the server)
                - 0x5d request rejected failed because client's identd (Identification Protocol Daemon) could not confirm user id string in the request
    3 - 2 bytes: arbitrary bytes, that should be ignored
    4 - 4 bytes: arbitrary bytes, that should be ignored

    i.e: Server replies OK
    00-5a-XX-XX-XX-XX-XX-XX

    SOCKS4a - connection response
    1 - 1 byte: null byte
    2 - 1 byte: status byte:
                - 0x5a request granted
                - 0x5b request rejected or failed
                - 0x5c request rejected failed because client is not running identd (Identification Protocol Daemon) (or not reached from the server)
                - 0x5d request rejected failed because client's identd (Identification Protocol Daemon) could not confirm user id string in the request
    3 - 2 bytes: arbitrary bytes, that should be ignored
    4 - 4 bytes: arbitrary bytes, that should be ignored

    i.e: Server replies OK
    00-5a-XX-XX-XX-XX-XX-XX
     */

    public Socks4ConnectionResponse(byte status) {
        version = SOCKS4_VERSION;
        this.status = status;

        messageBytes = ByteBuffer.allocate(8);

        // version
        messageBytes.put((byte) 0x00);
        // command
        messageBytes.put(status);
        // arbitrary bytes
        messageBytes.put(new byte[]{0x00, 0x00});
        // arbitrary bytes
        messageBytes.put(new byte[]{0x00, 0x00, 0x00, 0x00});

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
                "Socks4ConnectionResponse {\n" +
                "   version:                       " + version + "\n" +
                "   status:                        " + status + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }

}
