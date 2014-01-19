package org.mockserver.proxy.socks.message.socks4;

import org.mockserver.proxy.socks.message.SocksMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Socks4Message extends SocksMessage {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** SOCKS4 **/

    public static final byte SOCKS4_VERSION = 0x04;
    // connection request
    public static final byte SOCKS4_ESTABLISH_STREAM_CONNECTION = 0x01;
    public static final byte SOCKS4_ESTABLISH_PORT_BINDING = 0x02;
    // connection response
    public static final byte SOCKS4_REQUEST_GRANTED = 0x5a;
    public static final byte SOCKS4_REQUEST_REJECTED_OR_FAILED = 0x5b;
    public static final byte SOCKS4_REQUEST_REJECTED_CLIENT_NOT_RUNNING_IDENTD = 0x5b;
    public static final byte SOCKS4_REQUEST_REJECTED_CLIENT_IDENTD_COULD_NOT_CONFIRM_USER_ID = 0x5d;

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
    04-01-0050-42660763-4672656400

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

//    protected final ByteBuffer data;
//    protected final int version;
//    protected final int command;
//    protected final int addressType;
//    protected final String hostName;
//    protected final InetAddress ip;
//    protected final int port;
//    private final Logger logger = LoggerFactory.getLogger(this.getClass());
//
//    public Socks4Message(int command, InetAddress ip, int port) {
//        version = SOCKS5_VERSION;
//        this.command = command;
//        this.ip = ip;
//        this.port = port;
//
//        byte[] address;
//        if (ip == null) {
//            address = new byte[4];
//            Arrays.fill(address, (byte) 0);
//            hostName = "0.0.0.0";
//        } else {
//            address = ip.getAddress();
//            hostName = ip.getHostName();
//        }
//        addressType = address.length == SOCKS5_ADDRESS_TYPE_IPV4 ? SOCKS5_ADDRESS_TYPE_IPV4 : SOCKS5_ADDRESS_TYPE_IPV6;
//
//        data = ByteBuffer.allocate(6 + address.length);
//        // version
//        data.put((byte) version);
//        // command
//        data.put((byte) command);
//        // reserved byte
//        data.put((byte) 0x00);
//        // address type
//        data.put((byte) addressType);
//        // address
//        data.put(address);
//        // port
//        data.put((byte) (port >> 8));
//        data.put((byte) port);
//    }
//
//    public Socks4Message(int command, String hostName, int port) {
//        version = SOCKS5_VERSION;
//        this.command = command;
//        InetAddress ip = null;
//        if (RESOLVE_DOMAIN_NAME_TO_IP) {
//            try {
//                ip = InetAddress.getByName(hostName);
//            } catch (UnknownHostException uhe) {
//                logger.error("Exception resolving host name in SOCKS message", uhe);
//            }
//        }
//        this.ip = ip;
//        this.port = port;
//
//        byte address[] = hostName.getBytes();
//        this.hostName = hostName;
//        addressType = SOCKS5_ADDRESS_TYPE_DOMAIN_NAME;
//
//        data = ByteBuffer.allocate(6 + address.length);
//        // version
//        data.put((byte) version);
//        // command
//        data.put((byte) command);
//        // reserved byte
//        data.put((byte) 0x00);
//        // address type
//        data.put((byte) addressType);
//        // address length
//        data.put((byte) address.length);
//        // address
//        data.put(address);
//        // port
//        data.put((byte) (port >> 8));
//        data.put((byte) port);
//    }
//
//    public Socks4Message(ByteBuffer messageBytes) throws IOException {
//        data = null;
//
//        version = SOCKS5_VERSION;
//        command = messageBytes.get();
//        int reserved = messageBytes.get();
//        addressType = messageBytes.get();
//
//        byte address[];
//
//        InetAddress ip = null;
//        switch (addressType) {
//            case SOCKS5_ADDRESS_TYPE_IPV4:
//                address = new byte[SOCKS5_IPV4_FIELD_LENGTH];
//                messageBytes.get(address);
//                hostName = ipv4BytesToString(address);
//                break;
//            case SOCKS5_ADDRESS_TYPE_IPV6:
//                address = new byte[SOCKS5_IPV6_FIELD_LENGTH];
//                messageBytes.get(address);
//                hostName = bytes2IPV6(address);
//                break;
//            case SOCKS5_ADDRESS_TYPE_DOMAIN_NAME:
//                // next byte shows the length
//                address = new byte[messageBytes.get()];
//                messageBytes.get(address);
//                hostName = new String(address);
//                if (RESOLVE_DOMAIN_NAME_TO_IP) {
//                    try {
//                        ip = InetAddress.getByName(hostName);
//                    } catch (UnknownHostException uhe) {
//                        logger.error("Exception resolving host name in SOCKS message", uhe);
//                    }
//                }
//                break;
//            default:
//                throw new SocksException(SocksProxy.SOCKS_JUST_ERROR);
//        }
//        this.ip = ip;
//
//        port = (messageBytes.get() >> 8) + messageBytes.get();
//    }
//
//    public String toString() {
//        return "" +
//                "Socks5Message {\n" +
//                "   version:     " + version + "\n" +
//                "   command:     " + command + "\n" +
//                "   reserved:    0\n" +
//                "   addressType: " + addressType + "\n" +
//                "   hostName:    " + hostName + "\n" +
//                "   port:        " + port + "\n" +
//                "}";
//    }
}
