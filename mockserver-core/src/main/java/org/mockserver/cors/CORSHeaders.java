package org.mockserver.cors;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * @author jamesdbloom
 */
public class CORSHeaders {

    private static final String ANY_ORIGIN = "*";
    private static final String NULL_ORIGIN = "null";

    private final String corsAllowHeaders;
    private final String corsAllowMethods;
    private final boolean corsAllowCredentials;
    private final String corsMaxAge;

    public CORSHeaders(String corsAllowHeaders, String corsAllowMethods, boolean corsAllowCredentials, int corsMaxAge) {
        this(
            configuration()
                .corsAllowHeaders(corsAllowHeaders)
                .corsAllowMethods(corsAllowMethods)
                .corsAllowCredentials(corsAllowCredentials)
                .corsMaxAgeInSeconds(corsMaxAge)
        );
    }

    public CORSHeaders(Configuration configuration) {
        this.corsAllowHeaders = configuration.corsAllowHeaders();
        this.corsAllowMethods = configuration.corsAllowMethods();
        this.corsAllowCredentials = configuration.corsAllowCredentials();
        this.corsMaxAge = "" + configuration.corsMaxAgeInSeconds();
    }

    public static boolean isPreflightRequest(Configuration configuration, HttpRequest request) {
        final Headers headers = request.getHeaders();
        boolean isPreflightRequest = request.getMethod().getValue().equals(OPTIONS.name()) &&
            headers.containsEntry(HttpHeaderNames.ORIGIN.toString()) &&
            headers.containsEntry(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString());
        if (isPreflightRequest) {
            configuration.enableCORSForAPI(true);
        }
        return isPreflightRequest;
    }

    public void addCORSHeaders(HttpRequest request, HttpResponse response) {
        String origin = request.getFirstHeader(HttpHeaderNames.ORIGIN.toString());
        if (NULL_ORIGIN.equals(origin)) {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), NULL_ORIGIN);
        } else if (!origin.isEmpty() && corsAllowCredentials) {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin);
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), "true");
        } else {
            setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), ANY_ORIGIN);
        }
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(), corsAllowMethods);
        String allowHeaders = corsAllowHeaders;
        if (!request.getFirstHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS.toString()).isEmpty()) {
            allowHeaders += ", " + request.getFirstHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS.toString());
        }
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), allowHeaders);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), allowHeaders);
        setHeaderIfNotAlreadyExists(response, HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(), corsMaxAge);
    }

    private void setHeaderIfNotAlreadyExists(HttpResponse response, String name, String value) {
        if (response.getFirstHeader(name).isEmpty()) {
            response.withHeader(name, value);
        }
    }

}
