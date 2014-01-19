package org.mockserver.proxy.socks.message.socks5;

import org.mockserver.proxy.socks.message.SocksMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Socks5Message extends SocksMessage {

    /** SOCKS5 **/

    public static final byte SOCKS5_VERSION = 0x05;
    // authentication request
    public static final byte SOCKS5_NO_AUTHENTICATION = 0x00;
    public static final byte SOCKS5_GSSAPI_AUTHENTICATION = 0x01;
    public static final byte SOCKS5_USERNAME_PASSWORD_AUTHENTICATION = 0x02;
    public static final int SOCKS5_NO_ACCEPTABLE_AUTHENTICATION = 0xFF;
    // authentication response
    public static final byte SOCKS5_AUTHENTICATION_SUCCESS = 0x00;
    public static final byte SOCKS5_AUTHENTICATION_FAILURE = 0x01;
    // connection request
    public static final byte SOCKS5_ESTABLISH_STREAM_CONNECTION = 0x01;
    public static final byte SOCKS5_ESTABLISH_PORT_BINDING = 0x02;
    public static final byte SOCKS5_ESTABLISH_UDP_PORT = 0x03;
    // address type
    public static final byte SOCKS5_ADDRESS_TYPE_IPV4 = 0x01;
    public static final byte SOCKS5_ADDRESS_TYPE_DOMAIN_NAME = 0x03;
    public static final byte SOCKS5_ADDRESS_TYPE_IPV6 = 0x04;
    public static final byte SOCKS5_IPV4_FIELD_LENGTH = 4;
    public static final byte SOCKS5_IPV6_FIELD_LENGTH = 16;
    // connection response
    public static final byte SOCKS5_REQUEST_GRANTED = 0x00;
    public static final byte SOCKS5_REQUEST_REJECTED_GENERAL_FAILURE = 0x01;
    public static final byte SOCKS5_REQUEST_REJECTED_CONNECTION_NOT_ALLOWED_BY_RULE_SET = 0x02;
    public static final byte SOCKS5_REQUEST_REJECTED_NETWORK_UNREACHABLE = 0x03;
    public static final byte SOCKS5_REQUEST_REJECTED_HOST_UNREACHABLE = 0x04;
    public static final byte SOCKS5_REQUEST_REJECTED_CONNECTION_REFUSED_BY_DESTINATION_HOST = 0x05;
    public static final byte SOCKS5_REQUEST_REJECTED_TTL_EXPIRED = 0x06;
    public static final byte SOCKS5_REQUEST_REJECTED_COMMAND_NOT_SUPPORTED_OR_PROTOCOL_ERROR = 0x07;
    public static final byte SOCKS5_REQUEST_REJECTED_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;
    public static boolean RESOLVE_DOMAIN_NAME_TO_IP = true;
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
    SOCKS5 initial handshake consists of the following:
     - Client sends initial greeting listing supported authentication methods
     - Server chooses authentication method
     - Multiple message for authentication
     - Client sends connection request (similar to SOCKS4)
     - Server sends connection response (similar to SOCKS4)

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

    SOCKS5 - servers authentication choice
    1 - 1 byte: version number 0x05
    2 - 1 byte: authentication method

    i.e: 05-00

    SOCKS5 - Username/Password request
    1 - 1 byte: version number 0x05
    2 - 1 byte: username length
    3 - variable: username
    4 - 1 byte: password length
    4 - variable: password

    SOCKS5 - Username/Password response
    1 - 1 byte: version number 0x05
    2 - 1 byte: status code
                - 0x00 success
                - any other value is failure

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

    i.e: 05-01-00-03-0F-7777772E6578616D706C652E636F6D-005

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
     */

}
