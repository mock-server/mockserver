package org.mockserver.proxy;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;

/**
 * Handler that supports HTTP CONNECT
 *
 * @author jamesdbloom
 */
public class ConnectHandler extends HandlerWrapper {
    private static final Logger logger = LoggerFactory.getLogger(ConnectHandler.class);
    private Executor executor;
    private Scheduler scheduler;
    private ByteBufferPool bufferPool;
    private SelectorManager selector;

    public ConnectHandler() {
        this(null);
    }

    public ConnectHandler(Handler handler) {
        setHandler(handler);
    }

    @Override
    protected void doStart() throws Exception {
        if (executor == null) {
            this.executor = getServer().getThreadPool();
        }
        if (scheduler == null) {
            this.scheduler = new ScheduledExecutorScheduler();
            addBean(scheduler);
        }
        if (bufferPool == null) {
            this.bufferPool = new MappedByteBufferPool();
            addBean(bufferPool);
        }
        addBean(selector = new Manager(executor, scheduler, 1));
        selector.setConnectTimeout(maxTimeout());
        super.doStart();
    }

    /**
     * Handles CONNECT request
     *
     * @param baseRequest   Jetty-specific http request
     * @param request       the http request
     * @param response      the http response
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (HttpMethod.CONNECT.is(request.getMethod())) {
            logger.debug("CONNECT request for {}", request.getRequestURI());
            baseRequest.setHandled(true);
            try {

                SocketChannel channel = SocketChannel.open();
                channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);

                // todo fix hard coded proxy port
                channel.connect(new InetSocketAddress("127.0.0.1", 2082));

                AsyncContext asyncContext = request.startAsync();
                asyncContext.setTimeout(0);

                ConnectContext connectContext = new ConnectContext(request, response, asyncContext, HttpConnection.getCurrentConnection());
                selector.connect(channel, connectContext);
            } catch (IOException ioe) {
                onConnectFailure(request, response, null, ioe);
            }
        } else {
            super.handle(target, baseRequest, request, response);
        }
    }

    protected void onConnectSuccess(ConnectContext connectContext, UpstreamConnection upstreamConnection) {
        HttpConnection httpConnection = connectContext.httpConnection;
        ByteBuffer requestBuffer = httpConnection.getRequestBuffer();
        ByteBuffer buffer = BufferUtil.EMPTY_BUFFER;
        int remaining = requestBuffer.remaining();
        if (remaining > 0) {
            buffer = bufferPool.acquire(remaining, requestBuffer.isDirect());
            BufferUtil.flipToFill(buffer);
            buffer.put(requestBuffer);
            buffer.flip();
        }

        HttpServletRequest request = connectContext.request;

        EndPoint downstreamEndPoint = httpConnection.getEndPoint();
        DownstreamConnection downstreamConnection = new DownstreamConnection(downstreamEndPoint, executor, bufferPool, buffer);
        downstreamConnection.setInputBufferSize(bufferSize());

        upstreamConnection.connection = downstreamConnection;
        downstreamConnection.connection = upstreamConnection;
        logger.debug("Connection setup completed: {}<->{}", downstreamConnection, upstreamConnection);

        HttpServletResponse response = connectContext.response;
        sendConnectResponse(request, response, HttpServletResponse.SC_OK);

        upgradeConnection(request, response, downstreamConnection);
        connectContext.asyncContext.complete();
    }

    protected void onConnectFailure(HttpServletRequest request, HttpServletResponse response, AsyncContext asyncContext, Throwable failure) {
        logger.debug("CONNECT failed", failure);
        sendConnectResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        if (asyncContext != null) {
            asyncContext.complete();
        }
    }

    private void sendConnectResponse(HttpServletRequest request, HttpServletResponse response, int statusCode) {
        try {
            response.setStatus(statusCode);
            if (statusCode != HttpServletResponse.SC_OK) {
                response.setHeader(HttpHeader.CONNECTION.asString(), HttpHeaderValue.CLOSE.asString());
            }
            response.getOutputStream().close();
            logger.debug("CONNECT response sent {} {}", request.getProtocol(), response.getStatus());
        } catch (IOException ioe) {
            logger.trace("Exception while closing connection", ioe);
        }
    }

    private void upgradeConnection(HttpServletRequest request, HttpServletResponse response, Connection connection) {
        // Set the new connection as request attribute and change the status to 101
        // so that Jetty understands that it has to upgrade the connection
        request.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, connection);
        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
        logger.debug("Upgraded connection to {}", connection);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        dumpThis(out);
        dump(out, indent, getBeans(), TypeUtil.asList(getHandlers()));
    }

    protected static class ConnectContext {
        public final HttpServletRequest request;
        public final HttpServletResponse response;
        public final AsyncContext asyncContext;
        public final HttpConnection httpConnection;

        public ConnectContext(HttpServletRequest request, HttpServletResponse response, AsyncContext asyncContext, HttpConnection httpConnection) {
            this.request = request;
            this.response = response;
            this.asyncContext = asyncContext;
            this.httpConnection = httpConnection;
        }
    }

    protected class Manager extends SelectorManager {

        private Manager(Executor executor, Scheduler scheduler, int selectors) {
            super(executor, scheduler, selectors);
        }

        @Override
        protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException {
            return new SelectChannelEndPoint(channel, selector, selectionKey, getScheduler(), maxTimeout());
        }

        @Override
        public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
            logger.debug("Connected to {}", channel.getRemoteAddress());
            UpstreamConnection connection = new UpstreamConnection(ConnectHandler.this, endpoint, executor, bufferPool, (ConnectContext) attachment);
            connection.setInputBufferSize(bufferSize());
            return connection;
        }

        @Override
        protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
            ConnectContext connectContext = (ConnectContext) attachment;
            onConnectFailure(connectContext.request, connectContext.response, connectContext.asyncContext, ex);
        }
    }

}
