package org.mockserver.server;

import com.google.common.net.MediaType;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.responsewriter.ResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
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
        this.actionHandler = new ActionHandler(httpStateHandler, null);
    }

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        ResponseWriter responseWriter = new ServletResponseWriter(httpServletResponse);
        HttpRequest request = null;
        try {

            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);
            if (!httpStateHandler.handle(request, responseWriter, true)) {

                if (request.matches("PUT", "/status")) {

                    responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())), "application/json");

                } else if (request.matches("PUT", "/bind")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED);

                } else if (request.matches("PUT", "/stop")) {

                    responseWriter.writeResponse(request, NOT_IMPLEMENTED);

                } else {

                    actionHandler.processAction(request, responseWriter, null);

                }
            }
        } catch (IllegalArgumentException iae) {
            logger.error("Exception processing " + request, iae);
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()));
        }
    }

}
