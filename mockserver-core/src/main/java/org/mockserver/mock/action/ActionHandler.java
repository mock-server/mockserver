package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.MockServerLogger;
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
import static org.mockserver.scheduler.Scheduler.submit;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");

    private HttpStateHandler httpStateHandler;
    private MockServerLogger mockServerLogger;
    private HttpResponseActionHandler httpResponseActionHandler;
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler;
    private HttpResponseClassCallbackActionHandler httpResponseClassCallbackActionHandler;
    private HttpResponseObjectCallbackActionHandler httpResponseObjectCallbackActionHandler;
    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler;
    private HttpForwardClassCallbackActionHandler httpForwardClassCallbackActionHandler;
    private HttpForwardObjectCallbackActionHandler httpForwardObjectCallbackActionHandler;
    private HttpOverrideForwardedRequestActionHandler httpOverrideForwardedRequestCallbackActionHandler;
    private HttpErrorActionHandler httpErrorActionHandler;

    // forwarding
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();

    public ActionHandler(HttpStateHandler httpStateHandler) {
        this.httpStateHandler = httpStateHandler;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpResponseActionHandler = new HttpResponseActionHandler();
        this.httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(mockServerLogger);
        this.httpResponseClassCallbackActionHandler = new HttpResponseClassCallbackActionHandler(mockServerLogger);
        this.httpResponseObjectCallbackActionHandler = new HttpResponseObjectCallbackActionHandler(httpStateHandler);
        this.httpForwardActionHandler = new HttpForwardActionHandler(mockServerLogger);
        this.httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockServerLogger);
        this.httpForwardClassCallbackActionHandler = new HttpForwardClassCallbackActionHandler(mockServerLogger);
        this.httpForwardObjectCallbackActionHandler = new HttpForwardObjectCallbackActionHandler(httpStateHandler);
        this.httpOverrideForwardedRequestCallbackActionHandler = new HttpOverrideForwardedRequestActionHandler(mockServerLogger);
        this.httpErrorActionHandler = new HttpErrorActionHandler();
    }

    public void processAction(final HttpRequest request, final ResponseWriter responseWriter, final ChannelHandlerContext ctx, Set<String> localAddresses, boolean proxyRequest, final boolean synchronous) {
        Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (expectation != null && expectation.getAction() != null) {
            Action action = expectation.getAction();
            switch (action.getType()) {
                case RESPONSE: {
                    final HttpResponse httpResponse = (HttpResponse) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            HttpResponse response = httpResponseActionHandler.handle(httpResponse);
                            responseWriter.writeResponse(request, response, false);
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for response action:{}", response, request, httpResponse);
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
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated response action:{}", response, request, httpTemplate);
                        }
                    }, httpTemplate.getDelay(), synchronous);
                    break;
                }
                case RESPONSE_CLASS_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    final HttpClassCallback classCallback = (HttpClassCallback) action;
                    submit(new Runnable() {
                        public void run() {
                            HttpResponse response = httpResponseClassCallbackActionHandler.handle(classCallback, request);
                            responseWriter.writeResponse(request, response, false);
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for class callback action:{}", response, request, classCallback);
                        }
                    }, synchronous);
                    break;
                }
                case RESPONSE_OBJECT_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    final HttpObjectCallback objectCallback = (HttpObjectCallback) action;
                    submit(new Runnable() {
                        public void run() {
                            httpResponseObjectCallbackActionHandler.handle(objectCallback, request, responseWriter);
                        }
                    }, synchronous);
                    break;
                }
                case FORWARD: {
                    final HttpForward httpForward = (HttpForward) action;
                    schedule(new Runnable() {
                        public void run() {
                            final SettableFuture<HttpResponse> responseFuture = httpForwardActionHandler.handle(httpForward, request);
                            submit(responseFuture, new Runnable() {
                                public void run() {
                                    try {
                                        HttpResponse response = responseFuture.get();
                                        responseWriter.writeResponse(request, response, false);
                                        httpStateHandler.log(new RequestResponseLogEntry(request, response));
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for forward action:{}", response, request, httpForward);
                                    } catch (Exception ex) {
                                        mockServerLogger.error(request, ex, ex.getMessage());
                                    }
                                }
                            }, synchronous);
                        }
                    }, httpForward.getDelay(), synchronous);
                    break;
                }
                case FORWARD_TEMPLATE: {
                    final HttpTemplate httpTemplate = (HttpTemplate) action;
                    schedule(new Runnable() {
                        public void run() {
                            final SettableFuture<HttpResponse> responseFuture = httpForwardTemplateActionHandler.handle(httpTemplate, request);
                            submit(responseFuture, new Runnable() {
                                public void run() {
                                    try {
                                        HttpResponse response = responseFuture.get();
                                        responseWriter.writeResponse(request, response, false);
                                        httpStateHandler.log(new RequestResponseLogEntry(request, response));
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for templated forward action:{}", response, request, httpTemplate);
                                    } catch (Exception ex) {
                                        mockServerLogger.error(request, ex, ex.getMessage());
                                    }
                                }
                            }, synchronous);
                        }
                    }, httpTemplate.getDelay(), synchronous);
                    break;
                }
                case FORWARD_CLASS_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    final HttpClassCallback classCallback = (HttpClassCallback) action;
                    submit(new Runnable() {
                        public void run() {
                            final SettableFuture<HttpResponse> responseFuture = httpForwardClassCallbackActionHandler.handle(classCallback, request);
                            submit(responseFuture, new Runnable() {
                                public void run() {
                                    try {
                                        HttpResponse response = responseFuture.get();
                                        responseWriter.writeResponse(request, response, false);
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for class callback action:{}", response, request, classCallback);
                                    } catch (Exception ex) {
                                        mockServerLogger.error(request, ex, ex.getMessage());
                                    }
                                }
                            }, synchronous);
                        }
                    }, synchronous);
                    break;
                }
                case FORWARD_OBJECT_CALLBACK: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    final HttpObjectCallback objectCallback = (HttpObjectCallback) action;
                    submit(new Runnable() {
                        public void run() {
                            httpForwardObjectCallbackActionHandler.handle(objectCallback, request, responseWriter, synchronous);
                        }
                    }, synchronous);
                    break;
                }
                case FORWARD_REPLACE: {
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    final HttpOverrideForwardedRequest httpOverrideForwardedRequest = (HttpOverrideForwardedRequest) action;
                    schedule(new Runnable() {
                        public void run() {
                            final SettableFuture<HttpResponse> responseFuture = httpOverrideForwardedRequestCallbackActionHandler.handle(httpOverrideForwardedRequest, request);
                            submit(responseFuture, new Runnable() {
                                public void run() {
                                    try {
                                        HttpResponse response = responseFuture.get();
                                        responseWriter.writeResponse(request, response, false);
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for class callback action:{}", response, request, httpOverrideForwardedRequest);
                                    } catch (Exception ex) {
                                        mockServerLogger.error(request, ex, ex.getMessage());
                                    }
                                }
                            }, synchronous);
                        }
                    }, httpOverrideForwardedRequest.getDelay(), synchronous);
                    break;
                }
                case ERROR: {
                    final HttpError httpError = (HttpError) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            httpErrorActionHandler.handle(httpError, ctx);
                            mockServerLogger.info(request, "returning error:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for error action:{}", httpError, request, httpError);
                        }
                    }, httpError.getDelay(), synchronous);
                    break;
                }
            }
        } else if (proxyRequest || !localAddresses.contains(request.getFirstHeader(HOST.toString()))) {
            final InetSocketAddress remoteAddress = ctx != null ? ctx.channel().attr(REMOTE_SOCKET).get() : null;
            final SettableFuture<HttpResponse> responseFuture = httpClient.sendRequest(hopByHopHeaderFilter.onRequest(request), remoteAddress);
            submit(responseFuture, new Runnable() {
                public void run() {
                    try {
                        HttpResponse response = responseFuture.get();
                        if (response == null) {
                            response = notFoundResponse();
                        }
                        responseWriter.writeResponse(request, response, false);
                        httpStateHandler.log(new RequestResponseLogEntry(request, response));
                        mockServerLogger.info(
                            request,
                            "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " as curl:{}",
                            response,
                            request,
                            httpRequestToCurlSerializer.toCurl(request, remoteAddress)
                        );
                    } catch (Exception ex) {
                        mockServerLogger.error(request, ex, ex.getMessage());
                    }
                }
            }, synchronous);

        } else {
            responseWriter.writeResponse(request, notFoundResponse(), false);
            httpStateHandler.log(new RequestLogEntry(request));
            mockServerLogger.info(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", notFoundResponse(), request);
        }
    }
}
