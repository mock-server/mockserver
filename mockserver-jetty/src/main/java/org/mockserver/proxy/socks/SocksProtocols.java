package org.mockserver.proxy.socks;

import org.apache.commons.io.Charsets;

import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * @see <a href="https://github.com/airlift/airlift/blob/master/http-client/src/main/java/io/airlift/http/client/netty/socks/SocksProtocols.java">https://github.com/airlift/airlift/blob/master/http-client/src/main/java/io/airlift/http/client/netty/socks/SocksProtocols.java</a>
 * @see <a href="http://en.wikipedia.org/wiki/SOCKS">http://en.wikipedia.org/wiki/SOCKS</a>
 * @see <a href="http://www.openssh.org/txt/socks4.protocol">SOCKS Protocol Version 4</a>
 */
public class SocksProtocols
{
    public static final int SOCKS_VERSION_4 = 0x04;
    public static final int CONNECT = 0x01;
    public static final int REQUEST_GRANTED = 0x5a;
    public static final int REQUEST_FAILED = 0x5b;
    public static final int REQUEST_FAILED_NO_IDENTD = 0x5c;
    public static final int REQUEST_FAILED_USERID_NOT_CONFIRMED = 0x5d;

    public static ByteBuffer createSocks4packet(InetAddress address, int port)
    {
        if (address == null) {
            throw new IllegalArgumentException("address is null");
        }
        byte[] userBytes = System.getProperty("user.name", "").getBytes(Charsets.UTF_8);
        ByteBuffer handshake = ByteBuffer.allocate(9 + userBytes.length);
        handshake.put((byte)SOCKS_VERSION_4); // SOCKS version
        handshake.put((byte)CONNECT); // CONNECT
        handshake.put((byte)port); // port
        handshake.put(address.getAddress()); // remote address to connect to
        handshake.put(userBytes); // user name
        handshake.put((byte)0x00); // null terminating the string
        return handshake;
    }

    public static ByteBuffer createSock4aPacket(String hostName, int port)
    {
        if (hostName == null) {
            throw new IllegalArgumentException("hostName is null");
        }
        byte[] userBytes = System.getProperty("user.name", "").getBytes(Charsets.UTF_8);
        byte[] hostNameBytes = hostName.getBytes(Charsets.UTF_8);
        ByteBuffer handshake = ByteBuffer.allocate(10 + userBytes.length + hostNameBytes.length);
        handshake.put((byte)SOCKS_VERSION_4); // SOCKS version
        handshake.put((byte)CONNECT); // CONNECT
        handshake.put((byte)port); // port
        handshake.put((byte)0x00); // fake ip
        handshake.put((byte)0x00); // fake ip
        handshake.put((byte)0x00); // fake ip
        handshake.put((byte)0x01); // fake ip
        handshake.put(userBytes); // user name
        handshake.put((byte)0x00); // null terminating the string
        handshake.put(hostNameBytes); // remote host name to connect to
        handshake.put((byte)0x00); // null terminating the string
        return handshake;
    }
}
