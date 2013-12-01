package org.mockserver.mappers;

import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.HttpServerResponse;

/**
 * @author jamesdbloom
 */
public class HttpServerResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void mapHttpServerResponse(HttpResponse httpResponse, HttpServerResponse httpServerResponse) {
        setStatusCode(httpResponse, httpServerResponse);
        setHeaders(httpResponse, httpServerResponse);
        setCookies(httpResponse, httpServerResponse);
        setBody(httpResponse, httpServerResponse);
    }

    private void setStatusCode(HttpResponse httpResponse, HttpServerResponse httpServerResponse) {
        if (httpResponse.getStatusCode() != null) {
            httpServerResponse.setStatusCode(httpResponse.getStatusCode());
        }
    }

    private void setBody(HttpResponse httpResponse, HttpServerResponse httpServerResponse) {
        if (httpResponse.getBody() != null) {
            httpServerResponse.setChunked(true);
            httpServerResponse.write(httpResponse.getBody(), CharEncoding.UTF_8);
        }
    }

    private void setHeaders(HttpResponse httpResponse, HttpServerResponse httpServletResponse) {
        if (httpResponse.getHeaders() != null) {
            for (Header header : httpResponse.getHeaders()) {
                for (String value : header.getValues()) {
                    httpServletResponse.putHeader(header.getName(), value);
                }
            }
        }
    }

    private void setCookies(HttpResponse httpResponse, HttpServerResponse httpServletResponse) {
        if (httpResponse.getCookies() != null) {
            for (Cookie cookie : httpResponse.getCookies()) {
                for (String value : cookie.getValues()) {
                    httpServletResponse.putHeader("Set-Cookie", cookie.getName() + "=" + value);
                }
            }
        }
    }
}
