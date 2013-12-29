package org.mockserver.proxy.connect;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.mockserver.model.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

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
    private final int securePort;

    public ConnectHandler(Handler handler, int securePort) {
        setHandler(handler);
        this.securePort = securePort;
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
        addBean(selector = new Manager(executor, scheduler));
        selector.setConnectTimeout(maxTimeout());
        super.doStart();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (HttpMethod.CONNECT.is(request.getMethod())) {
            logger.debug("CONNECT request for {}", request.getRequestURI());
            baseRequest.setHandled(true);
            try {

                SocketChannel channel = SocketChannel.open();
                channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);
                channel.connect(new InetSocketAddress("127.0.0.1", securePort));

                AsyncContext asyncContext = request.startAsync();
                asyncContext.setTimeout(0);

                ConnectContext connectContext = new ConnectContext(request, response, asyncContext, HttpConnection.getCurrentConnection().getEndPoint());
                selector.connect(channel, connectContext);
            } catch (IOException ioe) {
                onConnectFailure(response, null, ioe);
            }
        } else {
            super.handle(target, baseRequest, request, response);
        }
    }

    protected void onConnectFailure(HttpServletResponse response, AsyncContext asyncContext, Throwable failure) {
        response.setStatus(HttpStatusCode.GATEWAY_TIMEOUT_504.code());
        if (asyncContext != null) {
            asyncContext.complete();
        }
        logger.debug("CONNECT failed", failure);
    }

    protected static class ConnectContext {
        public final HttpServletRequest request;
        public final HttpServletResponse response;
        public final AsyncContext asyncContext;
        public final EndPoint currentConnectionEndPoint;

        public ConnectContext(HttpServletRequest request, HttpServletResponse response, AsyncContext asyncContext, EndPoint currentConnectionEndPoint) {
            this.request = request;
            this.response = response;
            this.asyncContext = asyncContext;
            this.currentConnectionEndPoint = currentConnectionEndPoint;
        }
    }

    protected class Manager extends SelectorManager {

        private Manager(Executor executor, Scheduler scheduler) {
            super(executor, scheduler);
        }

        @Override
        protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException {
            return new SelectChannelEndPoint(channel, selector, selectionKey, getScheduler(), maxTimeout());
        }

        @Override
        public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
            logger.debug("Connected to {}", channel.getRemoteAddress());
            ConnectContext connectContext = (ConnectContext) attachment;
            return new UpstreamConnection(endpoint, executor, bufferPool, connectContext.response, connectContext.request, connectContext.asyncContext, connectContext.currentConnectionEndPoint);
        }

        @Override
        protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
            ConnectContext connectContext = (ConnectContext) attachment;
            onConnectFailure(connectContext.response, connectContext.asyncContext, ex);
        }
    }

}
