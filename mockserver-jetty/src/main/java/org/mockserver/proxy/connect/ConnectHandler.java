package org.mockserver.proxy.connect;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final ByteBufferPool BUFFER_POOL = new MappedByteBufferPool();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final int securePort;
    private Executor executor;
    private Scheduler scheduler;
    private SelectorManager selector;

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
        addBean(selector = new Manager(executor, scheduler));
        selector.setConnectTimeout(maxTimeout());
        super.doStart();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (HttpMethod.CONNECT.is(request.getMethod())) {
            logger.debug("CONNECT request for {}", request.getRequestURI());
            baseRequest.setHandled(true);

            ConnectContext connectContext = new ConnectContext(request, response, HttpConnection.getCurrentConnection().getEndPoint());

            try {
                SocketChannel channel = SocketChannel.open();
                channel.socket().setTcpNoDelay(true);
                channel.configureBlocking(false);
                channel.connect(new InetSocketAddress("127.0.0.1", securePort));
                selector.connect(channel, connectContext);
            } catch (IOException ioe) {
                connectContext.onConnectFailure(ioe);
            }
        } else {
            super.handle(target, baseRequest, request, response);
        }
    }

    protected class Manager extends SelectorManager {

        private final Executor executor;

        private Manager(Executor executor, Scheduler scheduler) {
            super(executor, scheduler);
            this.executor = executor;
        }

        @Override
        protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException {
            return new SelectChannelEndPoint(channel, selector, selectionKey, getScheduler(), maxTimeout());
        }

        @Override
        public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
            logger.debug("Connected to {}", channel.socket());
            return new ConnectConnection(endpoint, executor, (ConnectContext) attachment);
        }

        @Override
        protected void connectionFailed(SocketChannel channel, Throwable ex, Object attachment) {
            ((ConnectContext) attachment).onConnectFailure(ex);
        }
    }

}
