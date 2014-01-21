package org.mockserver.proxy.connect;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.proxy.connect.ConnectHandler.BUFFER_POOL;

public class ConnectConnection extends AbstractConnection {
    protected static final Logger logger = LoggerFactory.getLogger(ConnectConnection.class);
    protected final Executor executor;
    protected Connection connection;
    protected ConnectContext connectContext;

    protected ConnectConnection(EndPoint endPoint, Executor executor, ConnectContext connectContext) {
        super(endPoint, executor);
        this.executor = executor;
        this.connectContext = connectContext;
        setInputBufferSize(bufferSize());
    }

    @Override
    public void onFillable() {
        // Get a buffer
        final ByteBuffer requestBuffer = BUFFER_POOL.acquire(getInputBufferSize(), false);

        try {
            // can we read data?
            if (!getEndPoint().isInputShutdown()) {

                // read data
                final int filled = getEndPoint().fill(requestBuffer);

                if (filled > 0) { // data read

                    // send data downstream
                    connection.getEndPoint().write(new Callback() {

                        public void succeeded() {
                            // shall we read again?
                            fillInterested();
                        }

                        public void failed(Throwable t) {
                            connection.close();
                        }

                    }, requestBuffer);

                } else if (filled == 0) { // no data read

                    // shall we read again?
                    fillInterested();

                } else if (filled < 0) { // eof or end-point shutdown

                    // finished reading
                    connection.getEndPoint().shutdownOutput();

                }
            }
        } catch (IOException ioe) {
            logger.debug("IOException while proxying CONNECTION request to SSL endpoint", ioe);
            close();
            connection.close();
        } finally {
            BUFFER_POOL.release(requestBuffer);
        }
    }

    @Override
    public void onOpen() {
        super.onOpen();
        if (connectContext != null) {
            connectContext.onUpstreamOpen(this, executor);
        }
        fillInterested();
    }

    @Override
    public String toString() {
        return String.format("%s from: %s to: %s",
                super.toString(),
                getEndPoint().getLocalAddress().getHostName(),
                getEndPoint().getRemoteAddress().getHostName());
    }
}
