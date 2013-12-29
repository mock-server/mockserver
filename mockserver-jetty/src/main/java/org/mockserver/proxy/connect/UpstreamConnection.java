package org.mockserver.proxy.connect;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpConnection;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.Executor;

import static org.mockserver.configuration.SystemProperties.bufferSize;

/**
 * @author jamesdbloom
 */
public class UpstreamConnection extends ConnectConnection {
    private Executor executor;
    private ByteBufferPool bufferPool;
    private HttpServletResponse response;
    private HttpServletRequest request;
    private AsyncContext asyncContext;
    private EndPoint currentConnectionEndPoint;

    public UpstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, HttpServletResponse response, HttpServletRequest request, AsyncContext asyncContext, EndPoint currentConnectionEndPoint) {
        super(endPoint, executor, bufferPool);
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.response = response;
        this.request = request;
        this.asyncContext = asyncContext;
        this.currentConnectionEndPoint = currentConnectionEndPoint;
        setInputBufferSize(bufferSize());
    }

    @Override
    public void onOpen() {
        super.onOpen();

        DownstreamConnection downstreamConnection = new DownstreamConnection(currentConnectionEndPoint, executor, bufferPool, this);
        this.connection = downstreamConnection;

        response.setStatus(HttpServletResponse.SC_OK);
        logger.debug("Connected {} to {}", this, downstreamConnection);

        // Set new connection as request attribute and change status to 101 to tell Jetty to upgrade the connection
        request.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, downstreamConnection);
        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
        logger.debug("Requested connection upgrade for {}", downstreamConnection);

        asyncContext.complete();

        fillInterested();
    }
}
