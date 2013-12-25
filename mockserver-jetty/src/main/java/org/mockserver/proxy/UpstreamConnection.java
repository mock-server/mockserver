package org.mockserver.proxy;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;

import java.util.concurrent.Executor;

/**
 * @author jamesdbloom
 */
public class UpstreamConnection extends ProxyConnection {
    private ConnectHandler connectHandler;
    private ConnectHandler.ConnectContext connectContext;

    public UpstreamConnection(ConnectHandler connectHandler, EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConnectHandler.ConnectContext connectContext) {
        super(endPoint, executor, bufferPool);
        this.connectHandler = connectHandler;
        this.connectContext = connectContext;
    }

    @Override
    public void onOpen() {
        super.onOpen();
        connectHandler.onConnectSuccess(connectContext, this);
        fillInterested();
    }
}
