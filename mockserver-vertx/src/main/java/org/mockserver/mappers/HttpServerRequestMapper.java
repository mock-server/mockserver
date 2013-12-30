package org.mockserver.mappers;

import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.HttpServerRequest;

import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpServerRequestMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpRequest createHttpRequest(HttpServerRequest httpServerRequest, byte[] bodyBytes) {
        HttpRequest httpRequest = new HttpRequest();
        setMethod(httpRequest, httpServerRequest);
        setURL(httpRequest, httpServerRequest);
        setPath(httpRequest, httpServerRequest);
        setQueryString(httpRequest, httpServerRequest);
        setHeaders(httpRequest, httpServerRequest);
        setCookies(httpRequest, httpServerRequest);
        setBody(httpRequest, bodyBytes);
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, HttpServerRequest httpServletRequest) {
        httpRequest.withMethod(httpServletRequest.method());
    }

    private void setURL(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        httpRequest.withURL(httpServerRequest.uri());
    }

    private void setPath(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        httpRequest.withPath(httpServerRequest.path());
    }

    private void setQueryString(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        httpRequest.withQueryString(httpServerRequest.query());
    }

    private void setHeaders(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        List<Header> mappedHeaders = new ArrayList<>();
        MultiMap headers = httpServerRequest.headers();
        for (String headerName : headers.names()) {
            mappedHeaders.add(new Header(headerName, new ArrayList<>(headers.getAll(headerName))));
        }
        httpRequest.withHeaders(mappedHeaders);
    }

    private void setCookies(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        List<Cookie> mappedCookies = new ArrayList<>();
        MultiMap headers = httpServerRequest.headers();
        for (String headerName : headers.names()) {
            if (headerName.equals("Cookie") || headerName.equals("Set-Cookie")) {
                for (String cookieHeader : headers.getAll(headerName)) {
                    for (HttpCookie httpCookie : HttpCookie.parse(cookieHeader)) {
                        mappedCookies.add(new Cookie(httpCookie.getName(), httpCookie.getValue()));
                    }
                }
            }
        }
        httpRequest.withCookies(mappedCookies);
    }

    private void setBody(HttpRequest httpRequest, byte[] bodyBytes) {
        httpRequest.withBody(new String(bodyBytes, Charset.forName(CharEncoding.UTF_8)));
    }
}
