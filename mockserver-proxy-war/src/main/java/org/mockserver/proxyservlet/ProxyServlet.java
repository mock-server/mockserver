package org.mockserver.proxyservlet;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.servlet.responsewriter.ServletResponseWriter;
import org.slf4j.event.Level;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.rtsp.RtspResponseStatuses.NOT_IMPLEMENTED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.addSubjectAlternativeName;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet implements ServletContextListener {

    private MockServerLogger mockServerLogger;
    // generic handling
    private HttpStateHandler httpStateHandler;
    private Scheduler scheduler;
    // serializers
    private PortBindingSerializer portBindingSerializer;
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder;
    // mockserver
    private ActionHandler actionHandler;
    private EventLoopGroup workerGroup = new NioEventLoopGroup(ConfigurationProperties.nioEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-eventLoop"));

    @SuppressWarnings("WeakerAccess")
    public ProxyServlet() {
        this.mockServerLogger = new MockServerLogger(MockServerEventLog.class);
        this.httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder(this.mockServerLogger);
        this.scheduler = new Scheduler(mockServerLogger);
        this.httpStateHandler = new HttpStateHandler(this.mockServerLogger, this.scheduler);
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.portBindingSerializer = new PortBindingSerializer(mockServerLogger);
        this.actionHandler = new ActionHandler(workerGroup, httpStateHandler, null);
    }

    @Override
    public void destroy() {
        shutdown();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        shutdown();
    }

    private void shutdown() {
        this.scheduler.shutdown();
        if (!this.workerGroup.isShuttingDown()) {
            this.workerGroup.shutdownGracefully(100, 750, MILLISECONDS).syncUninterruptibly();
        }
        this.httpStateHandler.stop();
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        ResponseWriter responseWriter = new ServletResponseWriter(new MockServerLogger(), httpServletResponse);
        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            final String hostHeader = request.getFirstHeader(HOST.toString());
            if (isNotBlank(hostHeader)) {
                scheduler.submit(() -> addSubjectAlternativeName(hostHeader));
            }

            if (!httpStateHandler.handle(request, responseWriter, true)) {

                if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED, "ExpectationResponseCallback, ExpectationForwardCallback or ExpectationForwardAndResponseCallback is not supported by MockServer deployed as a WAR", "text/plain");

                } else if (request.matches("PUT", PATH_PREFIX + "/status", "/status")) {

                    responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())), "application/json");

                } else if (request.matches("PUT", PATH_PREFIX + "/bind", "/bind")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED);

                } else if (request.matches("PUT", PATH_PREFIX + "/stop", "/stop")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED);

                } else {

                    String portExtension = "";
                    if (!(httpServletRequest.getLocalPort() == 443 && httpServletRequest.isSecure() || httpServletRequest.getLocalPort() == 80)) {
                        portExtension = ":" + httpServletRequest.getLocalPort();
                    }
                    actionHandler.processAction(request, responseWriter, null, ImmutableSet.of(
                        httpServletRequest.getLocalAddr() + portExtension,
                        "localhost" + portExtension,
                        "127.0.0.1" + portExtension
                    ), true, true);

                }
            }
        } catch (IllegalArgumentException iae) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception processing:{}error:{}")
                    .setArguments(request, iae.getMessage())
            );
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception processing " + request)
                    .setThrowable(e)
            );
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()), true);
        }
    }
}
