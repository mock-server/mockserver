package org.mockserver.proxy.socks;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;

/**
 * @author jamesdbloom
 */
public class Pipe extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(Pipe.class);
    private final SocketChannel up;
    private final SocketChannel down;
    private final ByteBufferPool byteBufferPool;

    public Pipe(SocketChannel up, SocketChannel down, ByteBufferPool byteBufferPool) {
        super("Pipe from [" + up + "] to [" + down + "]");
        setDaemon(false);
        this.up = up;
        this.down = down;
        this.byteBufferPool = byteBufferPool;
    }

    @Override
    public void run() {
        try {
            transfer(up, down);
        } catch (IOException e) {
            logger.error("Exception transferring data between sockets", e);
        }

        IOUtils.closeQuietly(up);
        IOUtils.closeQuietly(down);
    }

    private void transfer(SocketChannel up, SocketChannel down) throws IOException {
        ByteBuffer socketBuffer = byteBufferPool.acquire(1024, false);

        try {
            while (up.isOpen() && down.isOpen() && up.read(socketBuffer) != -1) {
                socketBuffer.flip();
                down.write(socketBuffer);
                socketBuffer.clear();
            }
        } catch (AsynchronousCloseException ace) {
            // closed while blocking on a read, this is expected behaviour and is not an error
        }
    }
}
