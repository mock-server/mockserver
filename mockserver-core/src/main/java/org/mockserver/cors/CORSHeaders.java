package org.mockserver.cors;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;

/**
 * @author jamesdbloom
 */
public class CORSHeaders {

    private static final String ANY_ORIGIN = "*";
    private static final String NULL_ORIGIN = "null";

    public static boolean isPreflightRequest(HttpRequest request) {
        final Headers headers = request.getHeaders();
        boolean isPreflightRequest = request.getMethod().getValue().equals(OPTIONS.name()) &&
            headers.containsEntry(HttpHeaderNames.ORIGIN.toString()) &&
            headers.containsEntry(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString());
        if (isPreflightRequest) {
            enableCORSForAPI(true);
        }
        return isPreflightRequest;
    }

    public void addCORSHeaders(HttpRequest request, HttpResponse response) {
        String origin = request.getFirstHeader(HttpHeaderNames.ORIGIN.toString());
        if (NULL_ORIGIN.equals(origin)) {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), NULL_ORIGIN);
        } else if (!origin.isEmpty() && request.getFirstHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).equals("true")) {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin);
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), "true");
        } else {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), ANY_ORIGIN);
        }
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(), "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE");
        String defaultHeaders = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization";
        String allowHeaders = defaultHeaders;
        if (!request.getFirstHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS.toString()).isEmpty()) {
            allowHeaders += ", " + request.getFirstHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS.toString());
        }
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), allowHeaders);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), defaultHeaders);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "300");
    }

    private void setHeaderIfNotAlreadyExists(HttpResponse response, String name, String value) {
        if (response.getFirstHeader(name).isEmpty()) {
            response.withHeader(name, value);
        }
    }

}
