package org.mockserver.proxy.socks.message.socks4;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks4ConnectionRequest extends Socks4Message {
    private final byte[] bytes;
    private final byte version;
    private final byte command;
    private final String userId;
    private final InetSocketAddress socket;


    /*
    SOCKS4 - connection request
    1 - 1 byte: version number 0x04
    2 - 1 byte: command code
                - 0x01 establish TCP/IP stream connection
                - 0x02 establish TCP/IP port binding
    3 - 2 bytes: network byte order port number
    4 - 4 bytes: network byte order ip address
    5 - variable: user id string, terminated with null (0x00)

    i.e: Connect "Fred" to 66.102.7.99:80
    0x04 | 0x01 | 0x00 0x50 | 0x42 0x66 0x07 0x63 | 0x46 0x72 0x65 0x64 0x00

    SOCKS4a - connection request
    1 - 1 byte: version number 0x04
    2 - 1 byte: command code
                - 0x01 establish TCP/IP stream connection
                - 0x02 establish TCP/IP port binding
    3 - 2 bytes: network byte order port number
    4 - 4 bytes: deliberate incorrect ip address
                - 3 bytes of 0x00
                - 1 byte not 0x00
    5 - variable: user id string, terminated with null (0x00)
    5 - variable: domain name, terminated with null (0x00)
     */

    public Socks4ConnectionRequest(ByteBuffer messageBytes) {
        version = messageBytes.get();
        if (version != SOCKS4_VERSION) {
            throw new RuntimeException("Incorrect SOCKS4 version, expected [" + SOCKS4_VERSION + "] found [" + version + "]");
        }
        command = messageBytes.get();
        // big endian: (short) (messageBytes.get() << 8 | messageBytes.get() & 0xFF);
        // little endian: (short) (messageBytes.get() & 0xFF | messageBytes.get() << 8);
        short port = (short) (messageBytes.get() << 8 | messageBytes.get() & 0xFF);

        int addressLength = 4;
        byte[] address = new byte[addressLength];
        messageBytes.get(address);

        this.userId = readToNull(messageBytes);
        if (isSOCKS4a(address)) {
            try {
                this.socket = new InetSocketAddress(InetAddress.getByName(readToNull(messageBytes)), port);
            } catch (UnknownHostException uhe) {
                throw new RuntimeException("Exception resolving address [" + new String(address) + "] in SOCKS message", uhe);
            }
        } else {
            try {
                this.socket = new InetSocketAddress(InetAddress.getByAddress(address), port);
            } catch (UnknownHostException uhe) {
                throw new RuntimeException("Exception resolving address [" + new String(address) + "] in SOCKS message", uhe);
            }
        }

        if (messageBytes.hasArray()) {
            bytes = Arrays.copyOf(messageBytes.array(), 4 + addressLength + 2);
        } else {
            bytes = new byte[0];
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Received connection request:\n" + this);
        }
    }

    private boolean isSOCKS4a(byte[] address) {
        return address[0] == 0x00 && address[1] == 0x00 && address[2] == 0x00 && address[3] != 0x00;
    }

    private String readToNull(ByteBuffer messageBytes) {
        int capacity = 128;
        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        byte singleByte = messageBytes.get();
        while (singleByte != 0x00 && buffer.limit() < capacity) {
            buffer.put(singleByte);
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return new String(bytes);
    }

    public boolean isValid() {
        return true;
    }

    public InetSocketAddress address() {
        return socket;
    }

    public String toString() {
        return "" +
                "Socks4ConnectionRequest {\n" +
                "   version:                       " + version + "\n" +
                "   command:                       " + command + "\n" +
                "   socket:                        " + socket + "\n" +
                "   userId:                        " + userId + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }

}
