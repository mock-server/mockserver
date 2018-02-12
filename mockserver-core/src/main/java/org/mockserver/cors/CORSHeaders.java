package org.mockserver.cors;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPIHasBeenSetExplicitly;

/**
 * @author jamesdbloom
 */
public class CORSHeaders {

    private static final String ANY_ORIGIN = "*";
    private static final String NULL_ORIGIN = "null";

    public static boolean isPreflightRequest(HttpRequest request) {
        final Headers headers = request.getHeaders();
        return request.getMethod().getValue().equals(OPTIONS.name()) &&
            headers.containsEntry(HttpHeaderNames.ORIGIN.toString()) &&
            headers.containsEntry(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString());
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
        String headers = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization";
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), headers);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), headers);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), "300");
        if (!enableCORSForAPIHasBeenSetExplicitly()) {
            setHeaderIfNotAlreadyExists(response, "x-cors", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.enableCORSForAPI=false");
        }
    }

    private void setHeaderIfNotAlreadyExists(HttpResponse response, String name, String value) {
        if (response.getFirstHeader(name).isEmpty()) {
            response.withHeader(name, value);
        }
    }

}
