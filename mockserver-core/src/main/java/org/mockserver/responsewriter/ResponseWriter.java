package org.mockserver.responsewriter;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.configuration.Configuration;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.version.Version;
import org.slf4j.event.Level;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.INFO;
import static org.mockserver.mock.HttpState.PATH_PREFIX;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class ResponseWriter {

    protected final Configuration configuration;
    protected final MockServerLogger mockServerLogger;
    private final CORSHeaders corsHeaders;

    protected ResponseWriter(Configuration configuration, MockServerLogger mockServerLogger) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        corsHeaders = new CORSHeaders(configuration);
    }

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
        if (configuration.enableCORSForAllResponses()) {
            corsHeaders.addCORSHeaders(request, response);
        } else if (apiResponse && configuration.enableCORSForAPI()) {
            corsHeaders.addCORSHeaders(request, response);
        }
        String contentLengthHeader = response.getFirstHeader(CONTENT_LENGTH.toString());
        if (isNotBlank(contentLengthHeader)) {
            try {
                int contentLength = Integer.parseInt(contentLengthHeader);
                if (response.getBodyAsRawBytes().length > contentLength) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(INFO)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(request.getLogCorrelationId())
                            .setHttpRequest(request)
                            .setHttpResponse(response)
                            .setMessageFormat("returning response with content-length header " + contentLength + " which is smaller then response body length " + response.getBodyAsRawBytes().length + ", body will likely be truncated by client receiving request")
                    );
                }
            } catch (NumberFormatException ignore) {
                // ignore exception while parsing invalid content-length header
            }
        }
        if (apiResponse) {
            response.withHeader("version", Version.getVersion());
            final String path = request.getPath().getValue();
            if (!path.startsWith(PATH_PREFIX) && !path.equals(configuration.livenessHttpGetPath())) {
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
