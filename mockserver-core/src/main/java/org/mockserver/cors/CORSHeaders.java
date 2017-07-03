package org.mockserver.cors;

import org.mockserver.model.HttpResponse;

import javax.servlet.http.HttpServletResponse;

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
            response.withHeader("Access-Control-Max-Age", "1");
        }
        if (response.getFirstHeader("X-CORS").isEmpty()) {
            response.withHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
        }
    }

    public void addCORSHeaders(HttpServletResponse httpServletResponse) {
        String methods = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE";
        String headers = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary";
        if (httpServletResponse.getHeaders("Access-Control-Allow-Origin").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
        }
        if (httpServletResponse.getHeaders("Access-Control-Allow-Methods").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Methods", methods);
        }
        if (httpServletResponse.getHeaders("Access-Control-Allow-Headers").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Headers", headers);
        }
        if (httpServletResponse.getHeaders("Access-Control-Expose-Headers").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Expose-Headers", headers);
        }
        if (httpServletResponse.getHeaders("Access-Control-Max-Age").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Max-Age", "1");
        }
        if (httpServletResponse.getHeaders("X-CORS").isEmpty()) {
            httpServletResponse.setHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
        }
    }
}
