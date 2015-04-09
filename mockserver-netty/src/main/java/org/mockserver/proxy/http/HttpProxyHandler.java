package org.mockserver.proxy.http;

import com.google.common.base.Strings;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.LogFilter;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

@ChannelHandler.Sharable
public class HttpProxyHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final Proxy server;
    private final LogFilter logFilter;
    private final Filters filters = new Filters();
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public HttpProxyHandler(Proxy server, LogFilter logFilter) {
        super(false);
        this.server = server;
        this.logFilter = logFilter;
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), logFilter);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {

        try {

            if (request.getMethod().getValue().equals("CONNECT")) {

                // assume CONNECT always for SSL
                ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).set(Boolean.TRUE);
                // add Subject Alternative Name for SSL certificate
                ConfigurationProperties.addSslSubjectAlternativeNameDomains(
                        StringUtils.substringBefore(request.getPath().getValue(), ":")
                );
                ctx.pipeline().addLast(new HttpConnectHandler());
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(request);

            } else if (request.matches("PUT", "/status")) {

                writeResponse(ctx, request, HttpResponseStatus.OK);

            } else if (request.matches("PUT", "/clear")) {

                logFilter.clear(httpRequestSerializer.deserialize(request.getBodyAsString()));
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches("PUT", "/reset")) {

                logFilter.reset();
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches("PUT", "/dumpToLog")) {

                logFilter.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()), request.hasQueryStringParameter("type", "java"));
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches("PUT", "/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                writeResponse(ctx, request, HttpResponseStatus.OK, expectationSerializer.serialize(expectations), "application/json");

            } else if (request.matches("PUT", "/verify")) {

                String result = logFilter.verify(verificationSerializer.deserialize(request.getBodyAsString()));
                if (result.isEmpty()) {
                    writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                } else {
                    writeResponse(ctx, request, HttpResponseStatus.NOT_ACCEPTABLE, result, "plain/text");
                }

            } else if (request.matches("PUT", "/verifySequence")) {

                String result = logFilter.verify(verificationSequenceSerializer.deserialize(request.getBodyAsString()));
                if (result.isEmpty()) {
                    writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                } else {
                    writeResponse(ctx, request, HttpResponseStatus.NOT_ACCEPTABLE, result, "plain/text");
                }

            } else if (request.matches("PUT", "/stop")) {

                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                ctx.flush();
                ctx.close();
                server.stop();

            } else {

                HttpResponse response = sendRequest(filters.applyOnRequestFilters(request));
                if (logger.isInfoEnabled()) {
                    logger.info("\n returning response:\n{}\n for request:\n{}", request.toString().replaceAll("(?m)^", "\t"), request.toString().replaceAll("(?m)^", "\t"));
                }
                writeResponse(ctx, request, response);

            }
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
        }

    }

    private HttpResponse sendRequest(HttpRequest httpRequest) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            String hostHeader = httpRequest.getFirstHeader("Host");
            if (!Strings.isNullOrEmpty(hostHeader)) {
                String[] hostHeaderParts = hostHeader.split(":");

                Integer port = (httpRequest.isSecure() ? 443 : 80); // default
                if (hostHeaderParts.length > 1) {
                    port = Integer.parseInt(hostHeaderParts[1]);  // non-default
                }
                HttpResponse httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(outboundRequest(hostHeaderParts[0], port, "", httpRequest)));
                if (httpResponse != null) {
                    return httpResponse;
                }
            } else {
                logger.error("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + System.getProperty("line.separator") + httpRequest);
                throw new IllegalArgumentException("Host header must be provided for requests being forwarded");
            }
        }
        return notFoundResponse();
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus) {
        writeResponse(ctx, request, responseStatus, "", "application/json");
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType) {
        writeResponse(ctx, request,
                response()
                        .withStatusCode(responseStatus.code())
                        .withBody(body)
                        .updateHeader(header(HttpHeaders.Names.CONTENT_TYPE, contentType + "; charset=utf-8"))
        );
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        response.updateHeader(header(CONTENT_LENGTH, response.getBody().getRawBytes().length));
        if (request.isKeepAlive()) {
            response.updateHeader(header(CONNECTION, HttpHeaders.Values.KEEP_ALIVE));
            ctx.write(response);
        } else {
            response.updateHeader(header(CONNECTION, HttpHeaders.Values.CLOSE));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cause.getMessage().contains("Connection reset by peer")) {
            logger.warn("Exception caught by MockServer handler closing pipeline", cause);
        }
        ctx.close();
    }
}
