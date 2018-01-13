package org.mockserver.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.google.common.base.Charsets.UTF_8;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private LoggingFormatter logFormatter;
    // generic handling
    private HttpStateHandler httpStateHandler;
    // serializers
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    // mockserver
    private ActionHandler actionHandler;

    public MockServerServlet() {
        this.httpStateHandler = new HttpStateHandler();
        this.logFormatter = httpStateHandler.getLogFormatter();
        this.actionHandler = new ActionHandler(httpStateHandler);
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        ResponseWriter responseWriter = new ServletResponseWriter(httpServletResponse);
        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            if (!httpStateHandler.handle(request, responseWriter, true)) {

                if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED, "ExpectationResponseCallback and ExpectationForwardCallback is not supported by MockServer deployed as a WAR", "text/plain");

                } else if (request.matches("PUT", "/status")) {

                    responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())), "application/json");

                } else if (request.matches("PUT", "/bind")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED);

                } else if (request.matches("PUT", "/stop")) {

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
            logFormatter.errorLog(request, "Exception processing " + request + "\n" + iae.getMessage());
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            logFormatter.errorLog(request, e, "Exception processing " + request);
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()), true);
        }
    }

}
