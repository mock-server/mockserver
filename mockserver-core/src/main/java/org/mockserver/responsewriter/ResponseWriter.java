package org.mockserver.responsewriter;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;
import static org.mockserver.model.Header.header;

/**
 * @author jamesdbloom
 */
public abstract class ResponseWriter {

    public abstract void writeResponse(HttpRequest request, HttpResponseStatus responseStatus);

    public abstract void writeResponse(HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType);

    public abstract void writeResponse(HttpRequest request, HttpResponse response);

    protected void addConnectionHeader(HttpRequest request, HttpResponse response) {
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
}
