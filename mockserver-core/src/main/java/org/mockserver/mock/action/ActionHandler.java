package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
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

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.cors.CORSHeaders.isPreflightRequest;
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
    private NettyHttpClient httpClient;
    private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();

    public ActionHandler(HttpStateHandler httpStateHandler, ProxyConfiguration proxyConfiguration) {
        this.httpStateHandler = httpStateHandler;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpClient = new NettyHttpClient(proxyConfiguration);
        this.httpResponseActionHandler = new HttpResponseActionHandler();
        this.httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler(mockServerLogger);
        this.httpResponseClassCallbackActionHandler = new HttpResponseClassCallbackActionHandler(mockServerLogger);
        this.httpResponseObjectCallbackActionHandler = new HttpResponseObjectCallbackActionHandler(httpStateHandler);
        this.httpForwardActionHandler = new HttpForwardActionHandler(mockServerLogger, httpClient);
        this.httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler(mockServerLogger, httpClient);
        this.httpForwardClassCallbackActionHandler = new HttpForwardClassCallbackActionHandler(mockServerLogger, httpClient);
        this.httpForwardObjectCallbackActionHandler = new HttpForwardObjectCallbackActionHandler(httpStateHandler, httpClient);
        this.httpOverrideForwardedRequestCallbackActionHandler = new HttpOverrideForwardedRequestActionHandler(mockServerLogger, httpClient);
        this.httpErrorActionHandler = new HttpErrorActionHandler();
    }

    public void processAction(final HttpRequest request, final ResponseWriter responseWriter, final ChannelHandlerContext ctx, Set<String> localAddresses, boolean proxyThisRequest, final boolean synchronous) {
        final Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
        if (request.getHeaders().containsEntry("X-Forwarded-By", "MockServer")) {
            responseWriter.writeResponse(request, notFoundResponse().withHeader("X-Forwarded-By", "MockServer"), false);
            httpStateHandler.log(new RequestLogEntry(request));
            mockServerLogger.info(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", notFoundResponse(), request);
        } else if (expectation != null && expectation.getAction() != null) {
            Action action = expectation.getAction();
            switch (action.getType()) {
                case RESPONSE: {
                    final HttpResponse httpResponse = (HttpResponse) action;
                    httpStateHandler.log(new ExpectationMatchLogEntry(request, expectation));
                    schedule(new Runnable() {
                        public void run() {
                            HttpResponse response = httpResponseActionHandler.handle(httpResponse);
                            responseWriter.writeResponse(request, response, false);
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                            mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                                        mockServerLogger.info(request, "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", response, request, expectation);
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
                            mockServerLogger.info(request, "returning error:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " for expectation:{}", httpError, request, expectation);
                        }
                    }, httpError.getDelay(), synchronous);
                    break;
                }
            }
        } else if ((enableCORSForAPI() || enableCORSForAllResponses()) && isPreflightRequest(request)) {

            responseWriter.writeResponse(request, OK);

        } else if (proxyThisRequest || !localAddresses.contains(request.getFirstHeader(HOST.toString()))) {

            final InetSocketAddress remoteAddress = ctx != null ? ctx.channel().attr(REMOTE_SOCKET).get() : null;
            final HttpRequest clonedRequest = hopByHopHeaderFilter.onRequest(request);
            if (!proxyThisRequest) {
                clonedRequest.withHeader("X-Forwarded-By", "MockServer");
            }
            final SettableFuture<HttpResponse> responseFuture = httpClient.sendRequest(clonedRequest, remoteAddress);
            submit(responseFuture, new Runnable() {
                public void run() {
                    try {
                        HttpResponse response = responseFuture.get();
                        if (response == null) {
                            response = notFoundResponse();
                        }
                        responseWriter.writeResponse(request, response, false);
                        if (response.containsHeader("X-Forwarded-By", "MockServer")) {
                            httpStateHandler.log(new RequestLogEntry(request));
                            mockServerLogger.info(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", notFoundResponse(), request);
                        } else {
                            httpStateHandler.log(new RequestResponseLogEntry(request, response));
                            mockServerLogger.info(
                                request,
                                "returning response:{}" + NEW_LINE + " for request:{}" + NEW_LINE + " as curl:{}",
                                response,
                                request,
                                httpRequestToCurlSerializer.toCurl(request, remoteAddress)
                            );
                        }
                    } catch (Exception ex) {
                        if (ex.getCause() instanceof ConnectException || ex.getCause() instanceof SocketConnectionException) {
                            responseWriter.writeResponse(request, notFoundResponse(), false);
                            httpStateHandler.log(new RequestLogEntry(request));
                            mockServerLogger.info(request, "no matching expectation - returning:{}" + NEW_LINE + " for request:{}", notFoundResponse(), request);
                        } else {
                            mockServerLogger.error(request, ex, ex.getMessage());
                        }
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
