package org.mockserver.jetty.proxy.connect;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpConnection;
import org.mockserver.model.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * @author jamesdbloom
 */
class ConnectContext {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public final HttpServletRequest request;
    public final HttpServletResponse response;
    public final AsyncContext asyncContext;
    public final EndPoint currentConnectionEndPoint;

    public ConnectContext(HttpServletRequest request, HttpServletResponse response, EndPoint currentConnectionEndPoint) {
        this.request = request;
        this.response = response;
        this.asyncContext = request.startAsync();
        this.currentConnectionEndPoint = currentConnectionEndPoint;
    }

    protected void onConnectFailure(Throwable failure) {
        response.setStatus(HttpStatusCode.GATEWAY_TIMEOUT_504.code());
        asyncContext.complete();
        logger.debug("CONNECT failed", failure);
    }

    protected void onUpstreamOpen(ConnectConnection upstreamConnection, Executor executor) {
        ConnectConnection downstreamConnection = new ConnectConnection(currentConnectionEndPoint, executor, null);
        upstreamConnection.connection = downstreamConnection;
        downstreamConnection.connection = upstreamConnection;
        response.setStatus(HttpStatusCode.OK_200.code());
        try {
            response.getOutputStream().close();
        } catch (IOException ioe) {
            logger.trace("Exception while closing connection", ioe);
        }

        // Set new connection as request attribute and change status to 101 to tell Jetty to upgrade the connection
        request.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, downstreamConnection);
        response.setStatus(HttpStatusCode.SWITCHING_PROTOCOLS_101.code());
        logger.debug("Connected {} to {} requested connection upgrade", this, downstreamConnection);

        asyncContext.complete();
    }
}
