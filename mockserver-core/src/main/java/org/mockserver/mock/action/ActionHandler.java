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
import org.mockserver.mockserver.callback.ExpectationCallbackResponse;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpStateHandler httpStateHandler;
    private WebSocketClientRegistry webSocketClientRegistry;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private LoggingFormatter logFormatter = new LoggingFormatter(logger);
    private HttpResponseActionHandler httpResponseActionHandler;
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;
    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;
    private HttpCallbackActionHandler httpCallbackActionHandler;

    public ActionHandler(HttpStateHandler httpStateHandler, WebSocketClientRegistry webSocketClientRegistry) {
        this.httpStateHandler = httpStateHandler;
        this.webSocketClientRegistry = webSocketClientRegistry;
        httpResponseActionHandler = new HttpResponseActionHandler();
        httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler();
        httpForwardActionHandler = new HttpForwardActionHandler();
        httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler();
        httpCallbackActionHandler = new HttpCallbackActionHandler();
    }

    public void processAction(final HttpRequest request, final ResponseWriter responseWriter, @Nullable ChannelHandlerContext ctx) {
        HttpResponse response = notFoundResponse();
        final Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (expectation != null) {
            final Action action = expectation.getAction();
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
                case CALLBACK:
                    if (webSocketClientRegistry != null && action instanceof HttpObjectCallback) {
                        httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                        String clientId = ((HttpObjectCallback) action).getClientId();
                        webSocketClientRegistry.registerCallbackResponseHandler(clientId, new ExpectationCallbackResponse() {
                            @Override
                            public void handle(HttpResponse response) {
                                responseWriter.writeResponse(request, response.withConnectionOptions(connectionOptions().withCloseSocket(true)));
                                logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                            }
                        });
                        webSocketClientRegistry.sendClientMessage(clientId, request);
                    } else {
                        httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                        response = httpCallbackActionHandler.handle((HttpClassCallback) action, request);
                        responseWriter.writeResponse(request, response);
                        logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    }
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
                    if (ctx != null) {
                        httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                        HttpError httpError = ((HttpError) action).applyDelay();
                        if (httpError.getResponseBytes() != null) {
                            // write byte directly by skipping over HTTP codec
                            ChannelHandlerContext httpCodecContext = ctx.pipeline().context(HttpServerCodec.class);
                            if (httpCodecContext != null) {
                                httpCodecContext.writeAndFlush(Unpooled.wrappedBuffer(httpError.getResponseBytes())).awaitUninterruptibly();
                            }
                        }
                        if (httpError.getDropConnection()) {
                            ctx.close();
                        }
                        logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for action:{}", response, request, action);
                    } else {
                        responseWriter.writeResponse(request, response);
                    }
                    break;
            }
        } else {
            httpStateHandler.log(new RequestLogEntry(request));
            responseWriter.writeResponse(request, response);
        }
    }
}
