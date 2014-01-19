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
import static org.mockserver.proxy.socks.message.socks4.Socks4Message.SOCKS4_REQUEST_GRANTED;
import static org.mockserver.proxy.socks.message.socks4.Socks4Message.SOCKS4_VERSION;
import static org.mockserver.proxy.socks.message.socks5.Socks5Message.*;

/**
 * @author jamesdbloom
 */
public class SocksProxy {

    private static final Logger logger = LoggerFactory.getLogger(SocksProxy.class);
    private final ByteBufferPool byteBufferPool = new MappedByteBufferPool();

    public SocksProxy(int port) {
        try {
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
                serverSocketChannel.socket().bind(new InetSocketAddress(port));
                logger.debug("SOCKS Proxy waiting to receive request on [localhost:" + port + "]");
                while (serverSocketChannel.isOpen()) {
                    startSession(serverSocketChannel.accept());
                }
            }
        } catch (IOException ioe) {
            logger.debug("Exception creating request for port [" + port + "]", ioe);
        }
    }

    public static void main(String[] args) {
        SocksProxy socksProxy = new SocksProxy(2001);
    }

    private void startSession(final SocketChannel socketChannel) throws IOException {
        // read buffer
        int bufferCapacity = 512;
        ByteBuffer socketBuffer = ByteBuffer.allocate(bufferCapacity);
        socketChannel.read(socketBuffer);
        socketBuffer.flip(); // write to read
        printByteBuffer("one", socketBuffer);

        InetSocketAddress address = null;
        if (socketBuffer.get(0) == SOCKS5_VERSION) {
            Socks5StartSessionRequest startSessionRequest = new Socks5StartSessionRequest(socketBuffer);
            socketChannel.write(new Socks5StartSessionResponse(SOCKS5_NO_AUTHENTICATION).messageBytes());

            // read buffer
            socketBuffer = ByteBuffer.allocate(bufferCapacity);
            socketChannel.read(socketBuffer);
            socketBuffer.flip(); // write to read
            printByteBuffer("two", socketBuffer);

            Socks5ConnectionRequest connectionRequest = new Socks5ConnectionRequest(socketBuffer);
            address = connectionRequest.address();
            socketChannel.write(new Socks5ConnectionResponse(SOCKS5_REQUEST_GRANTED, connectionRequest.addressType(), address).messageBytes());


        } else if (socketBuffer.get(0) == SOCKS4_VERSION) {
            Socks4ConnectionRequest connectionRequest = new Socks4ConnectionRequest(socketBuffer);
            address = connectionRequest.address();
            socketChannel.write(new Socks4ConnectionResponse(SOCKS4_REQUEST_GRANTED).messageBytes());
        } else {
            throw new RuntimeException("Unrecognized SOCKS version number expected one of [" + Arrays.asList(printHexBinary(SOCKS5_VERSION), printHexBinary(SOCKS4_VERSION)) + "] but found [" + printHexBinary(socketBuffer.get(0)) + "]");
        }
        if (address != null) {
            startPipe(socketChannel, address);
        }
    }

    private void startPipe(final SocketChannel socketChannel, final SocketAddress socketAddress) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SocketChannel downStream = SocketChannel.open();
                    if (downStream.connect(socketAddress)) {
                        new Pipe(socketChannel, downStream, byteBufferPool).start();
                        new Pipe(downStream, socketChannel, byteBufferPool).start();
                    }
                } catch (Exception e) {
                    logger.error("Exception while creating socket pipe from [" + socketChannel + "] to [" + socketAddress + "]", e);
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
