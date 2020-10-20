package org.mockserver.responsewriter;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.mock.HttpState.PATH_PREFIX;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class ResponseWriter {

    private static final CORSHeaders CORS_HEADERS = new CORSHeaders();

    public void writeResponse(final HttpRequest request, final HttpResponseStatus responseStatus) {
        writeResponse(request, responseStatus, "", "application/json");
    }

    public void writeResponse(final HttpRequest request, final HttpResponseStatus responseStatus, final String body, final String contentType) {
        HttpResponse response = response()
            .withStatusCode(responseStatus.code())
            .withReasonPhrase(responseStatus.reasonPhrase())
            .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.replaceHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        writeResponse(request, response, true);
    }

    public void writeResponse(final HttpRequest request, HttpResponse response, final boolean apiResponse) {
        if (response == null) {
            response = notFoundResponse();
        }
        if (enableCORSForAllResponses()) {
            CORS_HEADERS.addCORSHeaders(request, response);
        } else if (apiResponse && enableCORSForAPI()) {
            CORS_HEADERS.addCORSHeaders(request, response);
        }
        if (apiResponse) {
            response.withHeader("version", org.mockserver.Version.getVersion());
            final String path = request.getPath().getValue();
            if (!path.startsWith(PATH_PREFIX) && !path.equals(ConfigurationProperties.livenessHttpGetPath())) {
                response.withHeader("deprecated",
                    "\"" + path + "\" is deprecated use \"" + PATH_PREFIX + path + "\" instead");
            }
        }

        sendResponse(request, addConnectionHeader(request, response));
    }

    public abstract void sendResponse(HttpRequest request, HttpResponse response);

    protected HttpResponse addConnectionHeader(final HttpRequest request, final HttpResponse response) {
        ConnectionOptions connectionOptions = response.getConnectionOptions();

        HttpResponse responseWithConnectionHeader = response.clone();

        if (connectionOptions != null && (connectionOptions.getSuppressConnectionHeader() != null || connectionOptions.getKeepAliveOverride() != null)) {
            if (!Boolean.TRUE.equals(connectionOptions.getSuppressConnectionHeader())) {
                if (Boolean.TRUE.equals(connectionOptions.getKeepAliveOverride())) {
                    responseWithConnectionHeader.replaceHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
                } else {
                    responseWithConnectionHeader.replaceHeader(header(CONNECTION.toString(), CLOSE.toString()));
                }
            }
        } else {
            if (Boolean.TRUE.equals(request.isKeepAlive())) {
                responseWithConnectionHeader.replaceHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            } else {
                responseWithConnectionHeader.replaceHeader(header(CONNECTION.toString(), CLOSE.toString()));
            }
        }

        return responseWithConnectionHeader;
    }
}
