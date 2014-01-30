package org.mockserver.mappers;

import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerToVertXResponseMapper {

    public void mapMockServerResponseToVertXResponse(HttpResponse httpResponse, HttpServerResponse httpServerResponse) {
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
            List<String> cookieValues = new ArrayList<String>();
            for (Cookie cookie : httpResponse.getCookies()) {
                for (String value : cookie.getValues()) {
                    cookieValues.add(cookie.getName() + "=" + value);
                }
            }
            if (!cookieValues.isEmpty()) {
                httpServletResponse.putHeader("Set-Cookie", cookieValues);
            }
        }
    }

    private void setBody(HttpResponse httpResponse, HttpServerResponse httpServerResponse) {
        if (httpResponse.getBody() != null) {
            httpServerResponse.setChunked(false);
            Buffer body = new Buffer(httpResponse.getBody());
            httpServerResponse.putHeader("Content-Length", "" + body.length());
            httpServerResponse.write(body);
        }
        httpServerResponse.end();
    }
}
