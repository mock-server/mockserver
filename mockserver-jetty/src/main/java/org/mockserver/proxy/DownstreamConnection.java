package org.mockserver.proxy;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * @author jamesdbloom
 */
public class DownstreamConnection extends ProxyConnection {
    private final ByteBuffer buffer;

    public DownstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ByteBuffer buffer) {
        super(endPoint, executor, bufferPool);
        this.buffer = buffer;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        final int remaining = buffer.remaining();
        connection.getEndPoint().write(new Callback() {
            @Override
            public void succeeded() {
                logger.debug("{} wrote initial {} bytes to server", DownstreamConnection.this, remaining);
                fillInterested();
            }

            @Override
            public void failed(Throwable x) {
                logger.debug(this + " failed to write initial " + remaining + " bytes to server", x);
                close();
                connection.close();
            }
        }, buffer);
    }
}
