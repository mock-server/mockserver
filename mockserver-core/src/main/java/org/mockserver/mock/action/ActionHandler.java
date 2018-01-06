package org.mockserver.mock.action;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;

import java.net.InetSocketAddress;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.scheduler.Scheduler.schedule;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");

    private HttpStateHandler httpStateHandler;
    private LoggingFormatter logFormatter;
    private HttpResponseActionHandler httpResponseActionHandler;
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;
    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;
    private HttpClassCallbackActionHandler httpClassCallbackActionHandler;
    private HttpObjectCallbackActionHandler httpObjectCallbackActionHandler;
    private HttpErrorActionHandler httpErrorActionHandler = new HttpErrorActionHandler();

    // forwarding
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();

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

    public void processAction(final HttpRequest request, final ResponseWriter responseWriter, final ChannelHandlerContext ctx, Set<String> localAddresses, boolean proxyRequest, boolean synchronous) {
        Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (expectation != null && expectation.getAction() != null) {
            Action action = expectation.getAction();
            switch (action.getType()) {
                case FORWARD: {
                    HttpResponse response = httpForwardActionHandler.handle((HttpForward) action, request);
                    responseWriter.writeResponse(request, response, false);
                    httpStateHandler.log(new RequestResponseLogEntry(request, response));
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for forward action:{}", response, request, action);
                    break;
                }
                case FORWARD_TEMPLATE: {
                    final HttpTemplate httpTemplate = (HttpTemplate) action;
                    schedule(new Runnable() {
                        public void run() {
                            HttpResponse response = httpForwardTemplateActionHandler.handle(httpTemplate, request);
                            responseWriter.writeResponse(request, response, false);
                            httpStateHandler.log(new RequestResponseLogEntry(request, response));
                            logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated forward action:{}", response, request, httpTemplate);
                        }
                    }, httpTemplate.getDelay(), synchronous);
                    break;
                }
                case OBJECT_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    httpObjectCallbackActionHandler.handle((HttpObjectCallback) action, request, responseWriter);
                    break;
                }
                case CLASS_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    HttpResponse response = httpClassCallbackActionHandler.handle((HttpClassCallback) action, request);
                    responseWriter.writeResponse(request, response, false);
                    logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for class callback action:{}", response, request, action);
                    break;
                }
                case RESPONSE: {
                    final HttpResponse httpResponse = (HttpResponse) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            HttpResponse response = httpResponseActionHandler.handle(httpResponse);
                            responseWriter.writeResponse(request, response, false);
                            logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for response action:{}", response, request, httpResponse);
                        }
                    }, httpResponse.getDelay(), synchronous);
                    break;
                }
                case RESPONSE_TEMPLATE: {
                    final HttpTemplate httpTemplate = (HttpTemplate) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            HttpResponse response = httpResponseTemplateActionHandler.handle(httpTemplate, request);
                            responseWriter.writeResponse(request, response, false);
                            logFormatter.infoLog(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated response action:{}", response, request, httpTemplate);
                        }
                    }, httpTemplate.getDelay(), synchronous);
                    break;
                }
                case ERROR: {
                    final HttpError httpError = (HttpError) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            httpErrorActionHandler.handle(httpError, ctx);
                            logFormatter.infoLog(request, "returning error:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for error action:{}", httpError, request, httpError);
                        }
                    }, httpError.getDelay(), synchronous);
                    break;
                }
            }
        } else if (proxyRequest || !localAddresses.contains(request.getFirstHeader(HOST.toString()))) {
            InetSocketAddress remoteAddress = ctx != null ? ctx.channel().attr(REMOTE_SOCKET).get() : null;
            HttpResponse response = httpClient.sendRequest(hopByHopHeaderFilter.onRequest(request), remoteAddress);
            if (response == null) {
                response = notFoundResponse();
            }
            responseWriter.writeResponse(request, response, false);
            httpStateHandler.log(new RequestResponseLogEntry(request, response));
            logFormatter.infoLog(
                request,
                "returning response:{}" + NEW_LINE + " for request as json:{}" + NEW_LINE + " as curl:{}",
                response,
                request,
                httpRequestToCurlSerializer.toCurl(request, remoteAddress)
            );
        } else {
            responseWriter.writeResponse(request, notFoundResponse(), false);
            httpStateHandler.log(new RequestLogEntry(request));
            logFormatter.infoLog(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", notFoundResponse(), request);
        }
    }
}
