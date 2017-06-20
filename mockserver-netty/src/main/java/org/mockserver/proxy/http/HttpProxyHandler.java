package org.mockserver.proxy.http;

import com.google.common.net.MediaType;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.socket.KeyStoreFactory;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.proxy.Proxy.REMOTE_SOCKET;
import static org.mockserver.proxy.error.Logging.shouldIgnoreException;

@ChannelHandler.Sharable
public class HttpProxyHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final Proxy server;
    private final RequestLogFilter requestLogFilter;
    private final RequestResponseLogFilter requestResponseLogFilter;
    private final boolean onwardSslStatusUnknown;
    private final Filters filters = new Filters();
    private LogFormatter logFormatter = new LogFormatter(logger);
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public HttpProxyHandler(Proxy server, RequestLogFilter requestLogFilter, RequestResponseLogFilter requestResponseLogFilter, Boolean onwardSslStatusUnknown) {
        super(false);
        this.server = server;
        this.requestLogFilter = requestLogFilter;
        this.requestResponseLogFilter = requestResponseLogFilter;
        this.onwardSslStatusUnknown = (onwardSslStatusUnknown != null ? onwardSslStatusUnknown : false);
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), requestLogFilter);
        filters.withFilter(new org.mockserver.model.HttpRequest(), requestResponseLogFilter);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {

        try {

            logFormatter.traceLog("received request:{}" + System.getProperty("line.separator"), request);

            if (request.getMethod().getValue().equals("CONNECT")) {

                // assume CONNECT always for SSL
                PortUnificationHandler.enabledSslUpstreamAndDownstream(ctx.channel());
                // add Subject Alternative Name for SSL certificate
                KeyStoreFactory.addSubjectAlternativeName(request.getPath().getValue());
                ctx.pipeline().addLast(new HttpConnectHandler(request.getPath().getValue(), -1));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(request);

            } else if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/status")) {

                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/clear")) {

                org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                requestLogFilter.clear(httpRequest);
                logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
                writeResponse(ctx, request, ACCEPTED);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                logFormatter.infoLog("resetting all expectations and request logs");
                writeResponse(ctx, request, ACCEPTED);

            } else if (request.matches("PUT", "/dumpToLog")) {

                requestResponseLogFilter.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()), request.hasQueryStringParameter("type", "java"));
                writeResponse(ctx, request, ACCEPTED);

            } else if (request.matches("PUT", "/retrieve")) {

                HttpRequest[] requests = requestLogFilter.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                writeResponse(ctx, request, OK, httpRequestSerializer.serialize(requests), "application/json");

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                logFormatter.infoLog("verifying:{}", verification);
                String result = requestLogFilter.verify(verification);
                if (result.isEmpty()) {
                    writeResponse(ctx, request, ACCEPTED);
                } else {
                    writeResponse(ctx, request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                }

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                logFormatter.infoLog("verifying sequence:{}", verificationSequence);
                if (result.isEmpty()) {
                    writeResponse(ctx, request, ACCEPTED);
                } else {
                    writeResponse(ctx, request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                }

            } else if (request.matches("PUT", "/stop")) {

                ctx.writeAndFlush(response().withStatusCode(ACCEPTED.code()));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        server.stop();
                    }
                }).start();

            } else {

                HttpRequest httpRequest = filters.applyOnRequestFilters(request);
                InetSocketAddress remoteAddress = ctx.channel().attr(REMOTE_SOCKET).get();

                // allow for filter to set request to null
                if (httpRequest != null) {
                    HttpResponse response = sendRequest(httpRequest, remoteAddress);
                    logFormatter.infoLog(
                            "returning response:{}" + System.getProperty("line.separator") + " for request as json:{}" + System.getProperty("line.separator") + " as curl:{}",
                            response,
                            request,
                            httpRequestToCurlSerializer.toCurl(httpRequest, remoteAddress)
                    );
                    writeResponse(ctx, request, response);
                } else {
                    writeResponse(ctx, request, notFoundResponse());
                }

            }
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()));
        }

    }

    private HttpResponse sendRequest(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
        HttpResponse httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(httpRequest, remoteAddress, true));
        // allow for filter to set response to null
        if (httpResponse == null) {
            httpResponse = notFoundResponse();
        }
        return httpResponse;
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus) {
        writeResponse(ctx, request, responseStatus, "", "application/json");
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType) {
        HttpResponse response = response()
                .withStatusCode(responseStatus.code())
                .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.updateHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        if (enableCORSForAPI()) {
            addCORSHeaders(response);
        }
        writeResponse(ctx, request, response);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders(response);
        }

        if (request.isKeepAlive() != null && request.isKeepAlive()) {
            response.updateHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            ctx.write(response);
        } else {
            response.updateHeader(header(CONNECTION.toString(), CLOSE.toString()));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void addCORSHeaders(HttpResponse response) {
        String methods = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE";
        String headers = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary";
        if (response.getFirstHeader("Access-Control-Allow-Origin").isEmpty()) {
            response.withHeader("Access-Control-Allow-Origin", "*");
        }
        if (response.getFirstHeader("Access-Control-Allow-Methods").isEmpty()) {
            response.withHeader("Access-Control-Allow-Methods", methods);
        }
        if (response.getFirstHeader("Access-Control-Allow-Headers").isEmpty()) {
            response.withHeader("Access-Control-Allow-Headers", headers);
        }
        if (response.getFirstHeader("Access-Control-Expose-Headers").isEmpty()) {
            response.withHeader("Access-Control-Expose-Headers", headers);
        }
        if (response.getFirstHeader("Access-Control-Max-Age").isEmpty()) {
            response.withHeader("Access-Control-Max-Age", "1");
        }
        if (response.getFirstHeader("X-CORS").isEmpty()) {
            response.withHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            logger.warn("Exception caught by HTTP proxy handler -> closing pipeline " + ctx.channel(), cause);
        }
        ctx.close();
    }
}
