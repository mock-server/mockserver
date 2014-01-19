package org.mockserver.proxy.socks.message.socks5;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks5ConnectionResponse extends Socks5Message {
    private final ByteBuffer messageBytes;
    private final byte[] bytes;
    private final byte version;
    private final byte status;
    private final byte addressType;
    private final byte[] address;
    private final int port;

    /*
    SOCKS5 - connection response
    1 - 1 byte: version number 0x05
    2 - 1 byte: status:
                - 0x00 request granted
                - 0x01 general failure
                - 0x02 connection not allowed by rule set
                - 0x03 network unreachable
                - 0x04 host unreachable
                - 0x05 connection refused by destination host
                - 0x06 TTL expired
                - 0x07 command not support / protocol error
                - 0x08 address type not supported
    3 - 1 byte: that should be ignored
    4 - 1 byte: address type
                - 0x01 IPv4
                - 0x03 domain name
                - 0x04 IPv6
    5 - variable: address
                - IPv4: 4 bytes
                - domain name: 1 byte: length + domain name
                - IPv6: 16 bytes
    6 - 2 bytes: network byte order port number

    i.e: 05-01-00-03-0F-7777772E6578616D706C652E636F6D-0050
     */

    public Socks5ConnectionResponse(byte status, byte addressType, InetSocketAddress socket) {
        version = SOCKS5_VERSION;
        this.status = status;
        this.addressType = addressType;
        int addressLength;
        switch (addressType) {
            case SOCKS5_ADDRESS_TYPE_IPV4:
                addressLength = SOCKS5_IPV4_FIELD_LENGTH;
                address = socket.getAddress().getAddress();
                break;
            case SOCKS5_ADDRESS_TYPE_IPV6:
                addressLength = SOCKS5_IPV4_FIELD_LENGTH;
                address = socket.getAddress().getAddress();
                break;
            case SOCKS5_ADDRESS_TYPE_DOMAIN_NAME:
                byte[] hostName = socket.getAddress().getHostAddress().getBytes();
                addressLength = 1 + hostName.length;
                address = new byte[addressLength];
                address[0] = (byte) hostName.length;
                System.arraycopy(hostName, 0, address, 1, hostName.length);
                break;
            default:
                throw new RuntimeException("Unknown address type expected [" + Arrays.asList(printHexBinary(SOCKS5_ADDRESS_TYPE_IPV4), printHexBinary(SOCKS5_ADDRESS_TYPE_IPV6), printHexBinary(SOCKS5_ADDRESS_TYPE_DOMAIN_NAME)));
        }
        messageBytes = ByteBuffer.allocate(4 + addressLength + 2);
        this.port = socket.getPort();

        // version
        messageBytes.put(version);
        // command
        messageBytes.put(status);
        // reserved byte
        messageBytes.put((byte) 0x00);
        // address type
        messageBytes.put(addressType);
        // address
        messageBytes.put(address);
        // port
        messageBytes.put((byte) (port >>> 8));
        messageBytes.put((byte) port);

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
                "Socks5ConnectionResponse {\n" +
                "   version:                       " + version + "\n" +
                "   status:                        " + status + "\n" +
                "   addressType:                   " + addressType + "\n" +
                "   address:                       " + printHexBinary(address) + "\n" +
                "   port:                          " + port + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }

}
