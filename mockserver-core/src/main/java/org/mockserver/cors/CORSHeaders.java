package org.mockserver.cors;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class CORSHeaders {

    public void addCORSHeaders(HttpResponse response) {
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
            response.withHeader("Access-Control-Max-Age", "300");
        }
        if (response.getFirstHeader("X-CORS").isEmpty()) {
            response.withHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
        }
    }
}
