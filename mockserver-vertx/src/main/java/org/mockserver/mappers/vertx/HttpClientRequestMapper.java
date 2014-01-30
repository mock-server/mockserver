package org.mockserver.mappers.vertx;

import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;

/**
 * @author jamesdbloom
 */
public class HttpClientRequestMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void mapHttpRequestToHttpClientRequest(HttpRequest httpRequest, HttpClientRequest httpClientRequest) {
        setBody(httpRequest, httpClientRequest);
        setHeaders(httpRequest, httpClientRequest);
        setCookies(httpRequest, httpClientRequest);
    }

    private void setHeaders(HttpRequest httpRequest, HttpClientRequest httpClientRequest) {
        if (httpRequest.getHeaders() != null) {
            for (Header header : httpRequest.getHeaders()) {
                for (String value : header.getValues()) {
                    httpClientRequest.putHeader(header.getName(), value);
                }
            }
        }
    }

    private void setCookies(HttpRequest httpRequest, HttpClientRequest httpClientRequest) {
        if (httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
                for (String value : cookie.getValues()) {
                    httpClientRequest.putHeader("Set-Cookie", cookie.getName() + "=" + value);
                }
            }
        }
    }

    private void setBody(HttpRequest httpRequest, HttpClientRequest httpClientRequest) {
        if (httpRequest.getBody() != null) {
            httpClientRequest.setChunked(false);
            Buffer body = new Buffer((httpRequest.getBody() != null ? httpRequest.getBody().toString() : ""));
            httpClientRequest.putHeader("Content-Length", "" + body.length());
            httpClientRequest.write(body);
        }
        httpClientRequest.end();
    }
}
