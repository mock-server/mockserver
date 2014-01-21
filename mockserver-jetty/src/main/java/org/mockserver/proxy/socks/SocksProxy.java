package org.mockserver.proxy.socks;

import com.google.common.base.Splitter;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.mockserver.proxy.socks.message.socks4.Socks4ConnectionRequest;
import org.mockserver.proxy.socks.message.socks4.Socks4ConnectionResponse;
import org.mockserver.proxy.socks.message.socks5.Socks5ConnectionRequest;
import org.mockserver.proxy.socks.message.socks5.Socks5ConnectionResponse;
import org.mockserver.proxy.socks.message.socks5.Socks5StartSessionRequest;
import org.mockserver.proxy.socks.message.socks5.Socks5StartSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import static org.mockserver.bytes.ByteUtils.printHexBinary;
import static org.mockserver.configuration.SystemProperties.extraHTTPPorts;
import static org.mockserver.configuration.SystemProperties.extraHTTPSPorts;
import static org.mockserver.proxy.socks.message.socks4.Socks4Message.SOCKS4_REQUEST_GRANTED;
import static org.mockserver.proxy.socks.message.socks4.Socks4Message.SOCKS4_VERSION;
import static org.mockserver.proxy.socks.message.socks5.Socks5Message.*;

/**
 * @author jamesdbloom
 */
public class SocksProxy {

    private static final Logger logger = LoggerFactory.getLogger(SocksProxy.class);
    private final ByteBufferPool byteBufferPool = new MappedByteBufferPool();
    private SocketAddress fixedDownStreamSocketAddress;
    private SocketAddress fixedDownStreamSecureSocketAddress;

    public SocksProxy(final int port) {
        this(port, null, null);
    }

    public SocksProxy(final int port, SocketAddress fixedDownStreamSocketAddress, SocketAddress fixedDownStreamSecureSocketAddress) {
        this.fixedDownStreamSocketAddress = fixedDownStreamSocketAddress;
        this.fixedDownStreamSecureSocketAddress = fixedDownStreamSecureSocketAddress;
        new Thread(new Runnable() {
            public void run() {
                try {
                    ServerSocketChannel serverSocketChannel = null;
                    try {
                        serverSocketChannel = ServerSocketChannel.open();

                        serverSocketChannel.socket().bind(new InetSocketAddress(port));
                        logger.debug("SOCKS Proxy waiting to receive request on [localhost:" + port + "]");
                        while (serverSocketChannel.isOpen()) {
                            readSocksRequests(serverSocketChannel.accept());
                        }
                    } finally {
                        if (serverSocketChannel != null) {
                            serverSocketChannel.close();
                        }
                    }
                } catch (IOException ioe) {
                    logger.debug("Exception creating request for port [" + port + "]", ioe);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        new SocksProxy(1099);
    }

    private void readSocksRequests(final SocketChannel socketChannel) throws IOException {
        // read buffer
        int bufferCapacity = 512;
        ByteBuffer socketBuffer = ByteBuffer.allocate(bufferCapacity);
        socketChannel.read(socketBuffer);
        socketBuffer.flip(); // write to read
        printByteBuffer("first request", socketBuffer);

        InetSocketAddress address = null;
        if (socketBuffer.get(0) == SOCKS5_VERSION) {
            if (new Socks5StartSessionRequest(socketBuffer).isValid()) {
                socketChannel.write(new Socks5StartSessionResponse(SOCKS5_NO_AUTHENTICATION).messageBytes());

                // read buffer
                socketBuffer = ByteBuffer.allocate(bufferCapacity);
                socketChannel.read(socketBuffer);
                socketBuffer.flip(); // write to read
                printByteBuffer("second request", socketBuffer);

                Socks5ConnectionRequest connectionRequest = new Socks5ConnectionRequest(socketBuffer);
                if (connectionRequest.isValid()) {
                    address = connectionRequest.address();
                    socketChannel.write(new Socks5ConnectionResponse(SOCKS5_REQUEST_GRANTED, connectionRequest.addressType(), address).messageBytes());
                }
            }
        } else if (socketBuffer.get(0) == SOCKS4_VERSION) {
            Socks4ConnectionRequest connectionRequest = new Socks4ConnectionRequest(socketBuffer);
            if (connectionRequest.isValid()) {
                address = connectionRequest.address();
                socketChannel.write(new Socks4ConnectionResponse(SOCKS4_REQUEST_GRANTED).messageBytes());
            }
        } else {
            throw new RuntimeException("Unrecognized SOCKS version number expected one of " + Arrays.asList(printHexBinary(SOCKS5_VERSION), printHexBinary(SOCKS4_VERSION)) + " but found [" + printHexBinary(socketBuffer.get(0)) + "]");
        }
        if (address != null) {
            if (isHTTP(address)) {
                startPipe(socketChannel, (fixedDownStreamSocketAddress == null ? address : fixedDownStreamSocketAddress));
            } else if (isHTTPS(address)) {
                startPipe(socketChannel, (fixedDownStreamSecureSocketAddress == null ? address : fixedDownStreamSecureSocketAddress));
            } else {
                logger.debug("Assuming traffic on port [" + address.getPort() + "] is not HTTP or HTTPS to configure this port as HTTP or HTTPS use -Dmockserver.extraHTTPPorts=<comma separated ports> or -Dmockserver.extraHTTPSPorts=<comma separated ports>");
                startPipe(socketChannel, address);
            }
        }
    }

    private boolean isHTTP(InetSocketAddress address) {
        return address.getPort() == 80 || address.getPort() == 8080 || extraHTTPPorts().contains(new Integer(address.getPort()));
    }

    private boolean isHTTPS(InetSocketAddress address) {
        return address.getPort() == 443 || address.getPort() == 1443 || extraHTTPSPorts().contains(new Integer(address.getPort()));
    }

    private void startPipe(final SocketChannel upStreamSocketChannel, final SocketAddress downStreamSocketAddress) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SocketChannel downStream = SocketChannel.open();
                    if (downStream.connect(downStreamSocketAddress)) {
                        new Pipe(upStreamSocketChannel, downStream, byteBufferPool).start();
                        new Pipe(downStream, upStreamSocketChannel, byteBufferPool).start();
                    }
                } catch (Exception e) {
                    logger.error("Exception while creating socket pipe from [" + upStreamSocketChannel + "] to [" + downStreamSocketAddress + "]", e);
                }
            }
        }).start();
    }

    private void printByteBuffer(String message, ByteBuffer socketBuffer) {
        if (logger.isDebugEnabled()) {
            byte[] rawBytes = socketBuffer.hasArray() ? socketBuffer.array() : new byte[0];
            String bytes = DatatypeConverter.printHexBinary(rawBytes);
            String bytesChunked = ":\n";
            for (String chunk : Splitter.fixedLength(64).split(bytes)) {
                bytesChunked += new StringBuffer(chunk).insert(48, '-').insert(32, '-').insert(16, '-').append('\n');
            }
            if (rawBytes[0] == 0x04 || rawBytes[0] == 0x05) {
                logger.debug(message + bytesChunked);
            } else {
                logger.debug(message + bytesChunked);
                logger.debug("\n" + new String(rawBytes));
            }
        }
    }
}
