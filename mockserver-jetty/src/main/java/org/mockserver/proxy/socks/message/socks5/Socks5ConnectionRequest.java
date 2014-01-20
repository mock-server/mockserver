package org.mockserver.proxy.socks.message.socks5;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.mockserver.bytes.ByteUtils.printHexBinary;

public class Socks5ConnectionRequest extends Socks5Message {
    private final byte[] bytes;
    private final byte version;
    private final byte command;
    private final byte addressType;
    private final InetSocketAddress socket;


    /*
    SOCKS5 - connection request
    1 - 1 byte: version number 0x05
    2 - 1 byte: command code
                - 0x01 establish TCP/IP stream connection
                - 0x02 establish TCP/IP port binding
                - 0x03 establish UDP port
    3 - 1 byte: reserved must be 0x00
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

    public Socks5ConnectionRequest(ByteBuffer messageBytes) {
        version = messageBytes.get();
        if (version != SOCKS5_VERSION) {
            throw new RuntimeException("Incorrect SOCKS5 version, expected [" + SOCKS5_VERSION + "] found [" + version + "]");
        }
        command = messageBytes.get();
        int reserved = messageBytes.get();
        addressType = messageBytes.get();

        byte[] address = new byte[0];

        InetAddress ip = null;
        int addressLength = 0;
        try {
            switch (addressType) {
                case SOCKS5_ADDRESS_TYPE_IPV4:
                    addressLength = SOCKS5_IPV4_FIELD_LENGTH;
                    address = new byte[addressLength];
                    messageBytes.get(address);
                    ip = InetAddress.getByAddress(address);
                    break;
                case SOCKS5_ADDRESS_TYPE_IPV6:
                    addressLength = SOCKS5_IPV4_FIELD_LENGTH;
                    address = new byte[addressLength];
                    messageBytes.get(address);
                    ip = InetAddress.getByAddress(address);
                    break;
                case SOCKS5_ADDRESS_TYPE_DOMAIN_NAME:
                    addressLength = messageBytes.get() + 1;
                    address = new byte[addressLength - 1];
                    messageBytes.get(address);
                    if (RESOLVE_DOMAIN_NAME_TO_IP) {
                        ip = InetAddress.getByName(new String(address));
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown address type expected [" + Arrays.asList(printHexBinary(SOCKS5_ADDRESS_TYPE_IPV4), printHexBinary(SOCKS5_ADDRESS_TYPE_IPV6), printHexBinary(SOCKS5_ADDRESS_TYPE_DOMAIN_NAME)));
            }
        } catch (UnknownHostException uhe) {
            throw new RuntimeException("Exception resolving address [" + new String(address) + "] in SOCKS message", uhe);
        }

        // big endian: (short) (messageBytes.get() << 8 | messageBytes.get() & 0xFF);
        // little endian: (short) (messageBytes.get() & 0xFF | messageBytes.get() << 8);
        short port = (short) (messageBytes.get() << 8 | messageBytes.get() & 0xFF);
        this.socket = new InetSocketAddress(ip, port);

        if (messageBytes.hasArray()) {
            bytes = Arrays.copyOf(messageBytes.array(), 4 + addressLength + 2);
        } else {
            bytes = new byte[0];
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Received connection request:\n" + this);
        }
    }

    public boolean isValid() {
        return true;
    }

    public byte addressType() {
        return addressType;
    }

    public InetSocketAddress address() {
        return socket;
    }

    public String toString() {
        return "" +
                "Socks5ConnectionRequest {\n" +
                "   version:                       " + version + "\n" +
                "   command:                       " + command + "\n" +
                "   addressType:                   " + addressType + "\n" +
                "   socket:                        " + socket + "\n" +
                "   bytes:                         " + printHexBinary(bytes) + "\n" +
                "}";
    }

}
