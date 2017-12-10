package org.mockserver.mockserver;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class NettyResponseWriter implements ResponseWriter {

    private CORSHeaders addCORSHeaders = new CORSHeaders();

    private final ChannelHandlerContext ctx;

    public NettyResponseWriter(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void writeResponse(HttpRequest request, HttpResponseStatus responseStatus) {
        writeResponse(request, responseStatus, "", "application/json");
    }

    @Override
    public void writeResponse(HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType) {
        HttpResponse response = response()
            .withStatusCode(responseStatus.code())
            .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.replaceHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        if (enableCORSForAPI()) {
            addCORSHeaders.addCORSHeaders(response);
        }
        writeResponse(request, response);
    }

    @Override
    public void writeResponse(HttpRequest request, HttpResponse response) {
        if (response == null) {
            response = notFoundResponse();
        }
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(response);
        }

        addConnectionHeader(request, response);

        writeAndCloseSocket(ctx, request, response);
    }

    private void addConnectionHeader(HttpRequest request, HttpResponse response) {
        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getKeepAliveOverride() != null) {
            if (connectionOptions.getKeepAliveOverride()) {
                response.replaceHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            } else {
                response.replaceHeader(header(CONNECTION.toString(), CLOSE.toString()));
            }
        } else if (connectionOptions == null || isFalseOrNull(connectionOptions.getSuppressConnectionHeader())) {
            if (request.isKeepAlive() != null && request.isKeepAlive()
                && (connectionOptions == null || isFalseOrNull(connectionOptions.getCloseSocket()))) {
                response.replaceHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            } else {
                response.replaceHeader(header(CONNECTION.toString(), CLOSE.toString()));
            }
        }
    }

    private void writeAndCloseSocket(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        boolean closeChannel;

        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getCloseSocket() != null) {
            closeChannel = connectionOptions.getCloseSocket();
        } else {
            closeChannel = !(request.isKeepAlive() != null && request.isKeepAlive());
        }

        if (closeChannel) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

}
