package org.mockserver.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.KeyAndCertificateFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private MockServerLogger mockServerLogger;
    // generic handling
    private HttpStateHandler httpStateHandler;
    private Scheduler scheduler = new Scheduler();
    // serializers
    private PortBindingSerializer portBindingSerializer;
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    // mockserver
    private ActionHandler actionHandler;

    public MockServerServlet() {
        this.httpStateHandler = new HttpStateHandler(scheduler);
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        portBindingSerializer = new PortBindingSerializer(mockServerLogger);
        this.actionHandler = new ActionHandler(httpStateHandler, null);
    }

    @Override
    protected void finalize() {
        scheduler.shutdown();
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        ResponseWriter responseWriter = new ServletResponseWriter(httpServletResponse);
        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            final String hostHeader = request.getFirstHeader(HOST.toString());
            scheduler.submit(new Runnable() {
                @Override
                public void run() {
                    KeyAndCertificateFactory.addSubjectAlternativeName(hostHeader);
                }
            });

            if (!httpStateHandler.handle(request, responseWriter, true)) {

                if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED, "ExpectationResponseCallback and ExpectationForwardCallback is not supported by MockServer deployed as a WAR", "text/plain");

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
                    ), false, true);

                }
            }
        } catch (IllegalArgumentException iae) {
            mockServerLogger.error(request, "exception processing: {} error: {}", request, iae.getMessage());
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            mockServerLogger.error(request, e, "exception processing " + request);
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()), true);
        }
    }

}
