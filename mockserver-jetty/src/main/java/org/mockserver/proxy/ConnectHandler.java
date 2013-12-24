package org.mockserver.proxy;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.proxy.ProxyConnection;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * Handler that supports HTTP CONNECT
 *
 * @author jamesdbloom
 */
public class ConnectHandler extends HandlerWrapper {
    protected static final Logger logger = Log.getLogger(ConnectHandler.class);
    private Executor executor;
    private Scheduler scheduler;
    private ByteBufferPool bufferPool;
    private SelectorManager selector;
    private long connectTimeout = 15000;
    private long idleTimeout = 30000;
    private int bufferSize = 4096;

    public ConnectHandler() {
        this(null);
    }

    public ConnectHandler(Handler handler) {
        setHandler(handler);
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ByteBufferPool getByteBufferPool() {
        return bufferPool;
    }

    public void setByteBufferPool(ByteBufferPool bufferPool) {
        this.bufferPool = bufferPool;
    }

    /**
     * @return the timeout, in milliseconds, to connect to the remote server
     */
    public long getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @param connectTimeout the timeout, in milliseconds, to connect to the remote server
     */
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * @return the idle timeout, in milliseconds
     */
    public long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * @param idleTimeout the idle timeout, in milliseconds
     */
    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    protected void doStart() throws Exception {
        if (executor == null) {
            setExecutor(getServer().getThreadPool());
        }
        if (scheduler == null) {
            setScheduler(new ScheduledExecutorScheduler());
            addBean(getScheduler());
        }
        if (bufferPool == null) {
            setByteBufferPool(new MappedByteBufferPool());
            addBean(getByteBufferPool());
        }
        addBean(selector = newSelectorManager());
        selector.setConnectTimeout(getConnectTimeout());
        super.doStart();
    }

    protected SelectorManager newSelectorManager() {
        return new Manager(getExecutor(), getScheduler(), 1);
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (HttpMethod.CONNECT.is(request.getMethod())) {
            String serverAddress = request.getRequestURI();
            logger.debug("CONNECT request for {}", serverAddress);
            try {
                handleConnect(baseRequest, request, response, serverAddress);
            } catch (Exception e) {
                logger.warn("ConnectHandler " + baseRequest.getUri() + " " + e);
            }
        } else {
            super.handle(target, baseRequest, request, response);
        }
    }

    /**
     * <p>Handles a CONNECT request.</p>
     * <p>CONNECT requests may have authentication headers such as {@code Proxy-Authorization}
     * that authenticate the client with the proxy.</p>
     *
     * @param jettyRequest  Jetty-specific http request
     * @param request       the http request
     * @param response      the http response
     * @param serverAddress the remote server address in the form {@code host:port}
     */
    protected void handleConnect(Request jettyRequest, HttpServletRequest request, HttpServletResponse response, String serverAddress) {
        jettyRequest.setHandled(true);
        try {
            String host = "127.0.0.1";
            int port = 2082;

            SocketChannel channel = SocketChannel.open();
            channel.socket().setTcpNoDelay(true);
            channel.configureBlocking(false);
            InetSocketAddress address = new InetSocketAddress(host, port);
            channel.connect(address);

            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0);

            logger.debug("Connecting to {}", address);
            ConnectContext connectContext = new ConnectContext(request, response, asyncContext, HttpConnection.getCurrentConnection());
            selector.connect(channel, connectContext);
        } catch (Exception x) {
            onConnectFailure(request, response, null, x);
        }
    }

    protected void onConnectSuccess(ConnectContext connectContext, UpstreamConnection upstreamConnection) {
        HttpConnection httpConnection = connectContext.getHttpConnection();
        ByteBuffer requestBuffer = httpConnection.getRequestBuffer();
        ByteBuffer buffer = BufferUtil.EMPTY_BUFFER;
        int remaining = requestBuffer.remaining();
        if (remaining > 0) {
            buffer = bufferPool.acquire(remaining, requestBuffer.isDirect());
            BufferUtil.flipToFill(buffer);
            buffer.put(requestBuffer);
            buffer.flip();
        }

        ConcurrentMap<String, Object> context = connectContext.getContext();
        HttpServletRequest request = connectContext.getRequest();
        prepareContext(request, context);

        EndPoint downstreamEndPoint = httpConnection.getEndPoint();
        DownstreamConnection downstreamConnection = newDownstreamConnection(downstreamEndPoint, context, buffer);
        downstreamConnection.setInputBufferSize(getBufferSize());

        upstreamConnection.setConnection(downstreamConnection);
        downstreamConnection.setConnection(upstreamConnection);
        logger.debug("Connection setup completed: {}<->{}", downstreamConnection, upstreamConnection);

        HttpServletResponse response = connectContext.getResponse();
        sendConnectResponse(request, response, HttpServletResponse.SC_OK);

        upgradeConnection(request, response, downstreamConnection);
        connectContext.getAsyncContext().complete();
    }

    protected void onConnectFailure(HttpServletRequest request, HttpServletResponse response, AsyncContext asyncContext, Throwable failure) {
        logger.debug("CONNECT failed", failure);
        sendConnectResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        if (asyncContext != null)
            asyncContext.complete();
    }

    private void sendConnectResponse(HttpServletRequest request, HttpServletResponse response, int statusCode) {
        try {
            response.setStatus(statusCode);
            if (statusCode != HttpServletResponse.SC_OK)
                response.setHeader(HttpHeader.CONNECTION.asString(), HttpHeaderValue.CLOSE.asString());
            response.getOutputStream().close();
            logger.debug("CONNECT response sent {} {}", request.getProtocol(), response.getStatus());
        } catch (IOException x) {
            // TODO: nothing we can do, close the connection
        }
    }

    protected DownstreamConnection newDownstreamConnection(EndPoint endPoint, ConcurrentMap<String, Object> context, ByteBuffer buffer) {
        return new DownstreamConnection(endPoint, getExecutor(), getByteBufferPool(), context, buffer);
    }

    protected UpstreamConnection newUpstreamConnection(EndPoint endPoint, ConnectContext connectContext) {
        return new UpstreamConnection(endPoint, getExecutor(), getByteBufferPool(), connectContext);
    }

    protected void prepareContext(HttpServletRequest request, ConcurrentMap<String, Object> context) {
    }

    private void upgradeConnection(HttpServletRequest request, HttpServletResponse response, Connection connection) {
        // Set the new connection as request attribute and change the status to 101
        // so that Jetty understands that it has to upgrade the connection
        request.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, connection);
        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
        logger.debug("Upgraded connection to {}", connection);
    }

    /**
     * <p>Reads (with non-blocking semantic) into the given {@code buffer} from the given {@code endPoint}.</p>
     *
     * @param endPoint the endPoint to read from
     * @param buffer   the buffer to read data into
     * @return the number of bytes read (possibly 0 since the read is non-blocking)
     *         or -1 if the channel has been closed remotely
     * @throws IOException if the endPoint cannot be read
     */
    protected int read(EndPoint endPoint, ByteBuffer buffer) throws IOException {
        return endPoint.fill(buffer);
    }

    /**
     * <p>Writes (with non-blocking semantic) the given buffer of data onto the given endPoint.</p>
     *
     * @param endPoint the endPoint to write to
     * @param buffer   the buffer to write
     * @param callback the completion callback to invoke
     */
    protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback) {
        logger.debug("{} writing {} bytes", this, buffer.remaining());
        endPoint.write(callback, buffer);
    }

    @Override
    public void dump(Appendable out, String indent) throws IOException {
        dumpThis(out);
        dump(out, indent, getBeans(), TypeUtil.asList(getHandlers()));
    }

    protected static class ConnectContext {
        private final ConcurrentMap<String, Object> context = new ConcurrentHashMap<>();
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final AsyncContext asyncContext;
        private final HttpConnection httpConnection;

        public ConnectContext(HttpServletRequest request, HttpServletResponse response, AsyncContext asyncContext, HttpConnection httpConnection) {
            this.request = request;
            this.response = response;
            this.asyncContext = asyncContext;
            this.httpConnection = httpConnection;
        }

        public ConcurrentMap<String, Object> getContext() {
            return context;
        }

        public HttpServletRequest getRequest() {
            return request;
        }

        public HttpServletResponse getResponse() {
            return response;
        }

        public AsyncContext getAsyncContext() {
            return asyncContext;
        }

        public HttpConnection getHttpConnection() {
            return httpConnection;
        }
    }

    protected class Manager extends SelectorManager {

        private Manager(Executor executor, Scheduler scheduler, int selectors) {
            super(executor, scheduler, selectors);
        }

        @Override
        protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException {
            return new SelectChannelEndPoint(channel, selector, selectionKey, getScheduler(), getIdleTimeout());
        }

        @Override
        public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
            ConnectHandler.logger.debug("Connected to {}", channel.getRemoteAddress());
            ConnectContext connectContext = (ConnectContext) attachment;
            UpstreamConnection connection = newUpstreamConnection(endpoint, connectContext);
            connection.setInputBufferSize(getBufferSize());
            return connection;
        }

        @Override
        protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
            ConnectContext connectContext = (ConnectContext) attachment;
            onConnectFailure(connectContext.request, connectContext.response, connectContext.asyncContext, ex);
        }
    }

    public class UpstreamConnection extends ProxyConnection {
        private ConnectContext connectContext;

        public UpstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConnectContext connectContext) {
            super(endPoint, executor, bufferPool, connectContext.getContext());
            this.connectContext = connectContext;
        }

        @Override
        public void onOpen() {
            super.onOpen();
            onConnectSuccess(connectContext, this);
            fillInterested();
        }

        @Override
        protected int read(EndPoint endPoint, ByteBuffer buffer) throws IOException {
            return ConnectHandler.this.read(endPoint, buffer);
        }

        @Override
        protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback) {
            ConnectHandler.this.write(endPoint, buffer, callback);
        }
    }

    public class DownstreamConnection extends ProxyConnection {
        private final ByteBuffer buffer;

        public DownstreamConnection(EndPoint endPoint, Executor executor, ByteBufferPool bufferPool, ConcurrentMap<String, Object> context, ByteBuffer buffer) {
            super(endPoint, executor, bufferPool, context);
            this.buffer = buffer;
        }

        @Override
        public void onOpen() {
            super.onOpen();
            final int remaining = buffer.remaining();
            write(getConnection().getEndPoint(), buffer, new Callback() {
                @Override
                public void succeeded() {
                    LOG.debug("{} wrote initial {} bytes to server", DownstreamConnection.this, remaining);
                    fillInterested();
                }

                @Override
                public void failed(Throwable x) {
                    LOG.debug(this + " failed to write initial " + remaining + " bytes to server", x);
                    close();
                    getConnection().close();
                }
            });
        }

        @Override
        protected int read(EndPoint endPoint, ByteBuffer buffer) throws IOException {
            return ConnectHandler.this.read(endPoint, buffer);
        }

        @Override
        protected void write(EndPoint endPoint, ByteBuffer buffer, Callback callback) {
            ConnectHandler.this.write(endPoint, buffer, callback);
        }
    }
}
