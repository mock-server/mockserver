package org.mockserver.proxy.connect;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;

import java.util.concurrent.Executor;

import static org.mockserver.configuration.SystemProperties.bufferSize;

/**
 * @author jamesdbloom
 */
public class DownstreamConnection extends ConnectionConnection {

    public DownstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, Connection upstreamConnection) {
        super(endPoint, executor, bufferPool);
        this.connection = upstreamConnection;
        setInputBufferSize(bufferSize());
    }

    @Override
    public void onOpen() {
        super.onOpen();
        fillInterested();
    }
}
