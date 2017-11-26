package org.mockserver.proxy.http;

import com.google.common.net.MediaType;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.PortBinding;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.List;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldIgnoreException;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.proxy.Proxy.REMOTE_SOCKET;

@ChannelHandler.Sharable
public class HttpProxyHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private Proxy server;
    private RequestLogFilter requestLogFilter;
    private Filters filters = new Filters();
    private NettyHttpClient httpClient = new NettyHttpClient();
    private HttpStateHandler httpStateHandler;
    // serializers
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // CORS
    private CORSHeaders addCORSHeaders = new CORSHeaders();

    public HttpProxyHandler(Proxy server, RequestLogFilter requestLogFilter, RequestResponseLogFilter requestResponseLogFilter) {
        super(false);
        this.server = server;
        this.requestLogFilter = requestLogFilter;
        filters.withFilter(request(), requestLogFilter);
        filters.withFilter(request(), requestResponseLogFilter);
        filters.withFilter(request(), new HopByHopHeaderFilter());
        httpStateHandler = new HttpStateHandler(requestLogFilter, requestResponseLogFilter, null);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {

        try {

            logFormatter.traceLog("received request:{}" + NEW_LINE, request);

            if (request.getMethod().getValue().equals("CONNECT")) {

                // assume CONNECT always for SSL
                PortUnificationHandler.enabledSslUpstreamAndDownstream(ctx.channel());
                // add Subject Alternative Name for SSL certificate
                KeyAndCertificateFactory.addSubjectAlternativeName(request.getPath().getValue());
                ctx.pipeline().addLast(new HttpConnectHandler(request.getPath().getValue(), -1));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(request);

            } else if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/status")) {

                writeResponse(ctx, request, OK, portBindingSerializer.serialize(portBinding(server.getPorts())), "application/json");

            } else if (request.matches("PUT", "/bind")) {

                PortBinding requestedPortBindings = portBindingSerializer.deserialize(request.getBodyAsString());
                try {
                    List<Integer> actualPortBindings = server.bindToPorts(requestedPortBindings.getPorts());
                    writeResponse(ctx, request, OK, portBindingSerializer.serialize(portBinding(actualPortBindings)), "application/json");
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof BindException) {
                        writeResponse(ctx, request, BAD_REQUEST, e.getMessage() + " port already in use", MediaType.create("text", "plain").toString());
                    } else {
                        throw e;
                    }
                }

            } else if (request.matches("PUT", "/clear")) {

                httpStateHandler.clear(request);
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/reset")) {

                httpStateHandler.reset();
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/dumpToLog")) {

                httpStateHandler.dumpRecordedRequestResponsesToLog(request);
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/retrieve")) {

                writeResponse(ctx, request, OK, httpStateHandler.retrieve(request),
                        JSON_UTF_8.toString().replace(request.hasQueryStringParameter("format", "java") ? "json" : "", "java")
                );

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verification);
                verifyResponse(ctx, request, result);
                logFormatter.infoLog("verifying requests that match:{}", verification);

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                verifyResponse(ctx, request, result);
                logFormatter.infoLog("verifying sequence that match:{}", verificationSequence);

            } else if (request.matches("PUT", "/stop")) {

                ctx.writeAndFlush(response().withStatusCode(OK.code()));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        server.stop();
                    }
                }).start();

            } else {

                InetSocketAddress remoteAddress = ctx.channel().attr(REMOTE_SOCKET).get();
                HttpResponse response = sendRequest(request, remoteAddress);
                writeResponse(ctx, request, response);

            }
        } catch (IllegalArgumentException iae) {
            logger.error("Exception processing " + request, iae);
            // send request without API CORS headers
            writeResponse(ctx, request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()));
        }
    }

    private void verifyResponse(ChannelHandlerContext ctx, HttpRequest request, String result) {
        if (result.isEmpty()) {
            writeResponse(ctx, request, ACCEPTED);
        } else {
            writeResponse(ctx, request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
        }
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
            addCORSHeaders.addCORSHeaders(response);
        }
        writeResponse(ctx, request, response);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(response);
        }

        if (request.isKeepAlive() != null && request.isKeepAlive()) {
            response.updateHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            ctx.write(response);
        } else {
            response.updateHeader(header(CONNECTION.toString(), CLOSE.toString()));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private HttpResponse sendRequest(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
        HttpResponse httpResponse = notFoundResponse();
        HttpRequest filteredRequest = filters.applyOnRequestFilters(httpRequest);
        // allow for filter to set response to null
        if (filteredRequest != null) {
            httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(filteredRequest, remoteAddress));
            if (httpResponse == null) {
                httpResponse = notFoundResponse();
            }
            logFormatter.infoLog(
                    "returning response:{}" + NEW_LINE + " for request as json:{}" + NEW_LINE + " as curl:{}",
                    httpResponse,
                    httpRequest,
                    httpRequestToCurlSerializer.toCurl(httpRequest, remoteAddress)
            );
        }
        return httpResponse;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            logger.warn("Exception caught by " + server.getClass() + " handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }
}
