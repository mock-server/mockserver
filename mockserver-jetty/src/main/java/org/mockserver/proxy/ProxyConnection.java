package org.mockserver.proxy;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ForkInvoker;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public abstract class ProxyConnection extends AbstractConnection {
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyRunner.class);
    private final ForkInvoker<Void> invoker = new ProxyForkInvoker();
    private final ByteBufferPool bufferPool;
    protected Connection connection;

    protected ProxyConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool) {
        super(endPoint, executor);
        this.bufferPool = bufferPool;
    }

    @Override
    public void onFillable() {
        final ByteBuffer buffer = bufferPool.acquire(getInputBufferSize(), true);
        try {
            final int filled = getEndPoint().fill(buffer);
            logger.debug("{} filled {} bytes", this, filled);
            if (filled > 0) {
                connection.getEndPoint().write(new Callback() {
                    @Override
                    public void succeeded() {
                        logger.debug("{} wrote {} bytes", this, filled);
                        bufferPool.release(buffer);
                        invoker.invoke(null);
                    }

                    @Override
                    public void failed(Throwable x) {
                        logger.debug(this + " failed to write " + filled + " bytes", x);
                        bufferPool.release(buffer);
                        connection.close();
                    }
                }, buffer);
            } else if (filled == 0) {
                bufferPool.release(buffer);
                fillInterested();
            } else {
                bufferPool.release(buffer);
                connection.getEndPoint().shutdownOutput();
            }
        } catch (IOException x) {
            logger.debug(this + " could not fill", x);
            bufferPool.release(buffer);
            close();
            connection.close();
        }
    }

    @Override
    public String toString() {
        return String.format("%s[l:%d<=>r:%d]",
                super.toString(),
                getEndPoint().getLocalAddress().getPort(),
                getEndPoint().getRemoteAddress().getPort());
    }

    private class ProxyForkInvoker extends ForkInvoker<Void> implements Runnable {
        private ProxyForkInvoker() {
            super(4);
        }

        @Override
        public void fork(Void arg) {
            getExecutor().execute(this);
        }

        @Override
        public void run() {
            onFillable();
        }

        @Override
        public void call(Void arg) {
            onFillable();
        }
    }
}
