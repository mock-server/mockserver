package org.mockserver.mock.action;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.slf4j.LoggerFactory;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpStateHandler httpStateHandler;
    private LoggingFormatter logFormatter = new LoggingFormatter(LoggerFactory.getLogger(this.getClass()));
    private HttpResponseActionHandler httpResponseActionHandler = new HttpResponseActionHandler();
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler();
    private HttpForwardActionHandler httpForwardActionHandler = new HttpForwardActionHandler();
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler();
    private HttpClassCallbackActionHandler httpClassCallbackActionHandler = new HttpClassCallbackActionHandler();
    private HttpObjectCallbackActionHandler httpObjectCallbackActionHandler;
    private HttpErrorActionHandler httpErrorActionHandler = new HttpErrorActionHandler();

    public ActionHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        httpObjectCallbackActionHandler = new HttpObjectCallbackActionHandler(httpStateHandler.getWebSocketClientRegistry());
    }

    public void processAction(HttpRequest request, ResponseWriter responseWriter, ChannelHandlerContext ctx) {
        HttpResponse response = notFoundResponse();
        Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (expectation != null) {
            Action action = expectation.getAction();
            switch (action.getType()) {
                case FORWARD:
                    response = httpForwardActionHandler.handle((HttpForward) action, request);
                    responseWriter.writeResponse(request, response);
                    httpStateHandler.log(new RequestResponseLogEntry(request, response));
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    break;
                case FORWARD_TEMPLATE:
                    response = httpForwardTemplateActionHandler.handle((HttpTemplate) action, request);
                    responseWriter.writeResponse(request, response);
                    httpStateHandler.log(new RequestResponseLogEntry(request, response));
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    break;
                case OBJECT_CALLBACK:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    httpObjectCallbackActionHandler.handle((HttpObjectCallback) action, request, responseWriter);
                    break;
                case CLASS_CALLBACK:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpClassCallbackActionHandler.handle((HttpClassCallback) action, request);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    break;
                case RESPONSE:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpResponseActionHandler.handle((HttpResponse) action);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    break;
                case RESPONSE_TEMPLATE:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpResponseTemplateActionHandler.handle((HttpTemplate) action, request);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    break;
                case ERROR:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    httpErrorActionHandler.handle((HttpError) action, ctx);
                    logFormatter.infoLog("returning error :{}" + NEW_LINE + " for request:{}", action, request);
                    break;
            }
        } else {
            httpStateHandler.log(new RequestLogEntry(request));
            responseWriter.writeResponse(request, response);
        }
    }
}
