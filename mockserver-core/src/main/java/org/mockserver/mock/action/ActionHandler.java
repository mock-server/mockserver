package org.mockserver.mock.action;

import io.netty.channel.ChannelHandlerContext;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpStateHandler httpStateHandler;
    private LoggingFormatter logFormatter;
    private HttpResponseActionHandler httpResponseActionHandler;
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;
    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;
    private HttpClassCallbackActionHandler httpClassCallbackActionHandler;
    private HttpObjectCallbackActionHandler httpObjectCallbackActionHandler;
    private HttpErrorActionHandler httpErrorActionHandler = new HttpErrorActionHandler();

    public ActionHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        this.logFormatter = httpStateHandler.getLogFormatter();
        this.httpResponseActionHandler = new HttpResponseActionHandler();
        this.httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(logFormatter);
        this.httpForwardActionHandler = new HttpForwardActionHandler();
        this.httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(logFormatter);
        this.httpClassCallbackActionHandler = new HttpClassCallbackActionHandler();
        this.httpObjectCallbackActionHandler = new HttpObjectCallbackActionHandler(httpStateHandler);
    }

    public void processAction(HttpRequest request, ResponseWriter responseWriter, ChannelHandlerContext ctx) {
        HttpResponse response = notFoundResponse();
        Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (expectation != null && expectation.getAction() != null) {
            Action action = expectation.getAction();
            switch (action.getType()) {
                case FORWARD:
                    response = httpForwardActionHandler.handle((HttpForward) action, request);
                    responseWriter.writeResponse(request, response);
                    httpStateHandler.log(new RequestResponseLogEntry(request, response));
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for forward action:{}", response, request, action);
                    break;
                case FORWARD_TEMPLATE:
                    response = httpForwardTemplateActionHandler.handle((HttpTemplate) action, request);
                    responseWriter.writeResponse(request, response);
                    httpStateHandler.log(new RequestResponseLogEntry(request, response));
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated forward action:{}", response, request, action);
                    break;
                case OBJECT_CALLBACK:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    httpObjectCallbackActionHandler.handle((HttpObjectCallback) action, request, responseWriter);
                    break;
                case CLASS_CALLBACK:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpClassCallbackActionHandler.handle((HttpClassCallback) action, request);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for class callback action:{}", response, request, action);
                    break;
                case RESPONSE:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpResponseActionHandler.handle((HttpResponse) action);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for response action:{}", response, request, action);
                    break;
                case RESPONSE_TEMPLATE:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    response = httpResponseTemplateActionHandler.handle((HttpTemplate) action, request);
                    responseWriter.writeResponse(request, response);
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated response action:{}", response, request, action);
                    break;
                case ERROR:
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    httpErrorActionHandler.handle((HttpError) action, ctx);
                    logFormatter.infoLog(request, "returning error:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for error action:{}", action, request, action);
                    break;
            }
        } else {
            httpStateHandler.log(new RequestLogEntry(request));
            logFormatter.infoLog(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", response, request);
            responseWriter.writeResponse(request, response);
        }
    }
}
